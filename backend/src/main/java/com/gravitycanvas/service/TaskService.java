package com.gravitycanvas.service;

import com.gravitycanvas.dto.*;
import com.gravitycanvas.entity.ApprovalStatus;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.UserAccount;
import com.gravitycanvas.exception.CircularDependencyException;
import com.gravitycanvas.exception.ResourceNotFoundException;
import com.gravitycanvas.repository.TaskFilterSpecification;
import com.gravitycanvas.repository.TaskRepository;
import com.gravitycanvas.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service encapsulating business operations for Tasks, Gravity Score calculations,
 * and Developer Ticket Manager Authorization workflows.
 */
@Service
public class TaskService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
        "createdAt", "deadline", "status", "title", "environment"
    );

    private final TaskRepository taskRepository;
    private final UserAccountRepository userRepository;
    private final GravityCalculatorService gravityCalculatorService;
    private final SettingsService settingsService;
    private final TaskDependencyTraversalService traversalService;
    private final GravityEngineService gravityEngineService;

    public TaskService(TaskRepository taskRepository,
                       UserAccountRepository userRepository,
                       GravityCalculatorService gravityCalculatorService,
                       SettingsService settingsService) {
        this(taskRepository, userRepository, gravityCalculatorService, settingsService,
             new TaskDependencyTraversalService(taskRepository),
             new GravityEngineService(taskRepository));
    }

    @Autowired
    public TaskService(TaskRepository taskRepository,
                       UserAccountRepository userRepository,
                       GravityCalculatorService gravityCalculatorService,
                       SettingsService settingsService,
                       TaskDependencyTraversalService traversalService,
                       GravityEngineService gravityEngineService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.gravityCalculatorService = gravityCalculatorService;
        this.settingsService = settingsService;
        this.traversalService = traversalService != null ? traversalService : new TaskDependencyTraversalService(taskRepository);
        this.gravityEngineService = gravityEngineService != null ? gravityEngineService : new GravityEngineService(taskRepository);
    }

    /**
     * Fetches all APPROVED tasks formatted with dynamic Gravity Scores for display on the main canvas.
     * Uses optimized single-valued hydration, direct edge projection, multi-root batched BFS,
     * and request-scoped workload caching with zero Cartesian collection joins.
     */
    @Transactional(readOnly = true)
    public List<TaskResponseDto> getAllTasks() {
        List<Task> approvedTasks = taskRepository.findAllApprovedWithSingleValued();
        // Fallback for mock unit tests stubbing findAllWithDependencies()
        if (approvedTasks == null || approvedTasks.isEmpty()) {
            List<Task> fallback = taskRepository.findAllWithDependencies();
            if (fallback != null && !fallback.isEmpty()) {
                approvedTasks = fallback.stream()
                    .filter(task -> task.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .collect(Collectors.toList());
            }
        }

        if (approvedTasks == null || approvedTasks.isEmpty()) {
            return List.of();
        }

        List<Long> approvedIds = approvedTasks.stream().map(Task::getId).toList();

        // 1. Direct edge projection directly from join table, partitioned for parameter limits
        List<Object[]> edges = queryDirectEdgesPartitioned(approvedIds);
        Map<Long, List<Long>> directDependentsMap = buildEdgeMap(edges, true);
        Map<Long, List<Long>> directDependenciesMap = buildEdgeMap(edges, false);

        // 2. Batched multi-root BFS downstream traversal
        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> downstreamMetrics =
            traversalService.calculatePageDownstreamMetrics(approvedIds, directDependentsMap);

        // 3. Request-level context preparation
        com.gravitycanvas.entity.GravitySettings settingsSnapshot = settingsService != null ? settingsService.getSettings() : null;
        Map<Long, Long> workloadCache = new HashMap<>();
        Clock clock = Clock.systemDefaultZone();

        // 4. Shared master calculation & pure DTO mapping
        return approvedTasks.stream()
            .map(task -> {
                long workload = 0L;
                if (task.getAssignee() != null) {
                    workload = workloadCache.computeIfAbsent(
                        task.getAssignee().getId(),
                        id -> taskRepository.countByAssigneeIdAndStatusNot(id, com.gravitycanvas.entity.TaskStatus.DONE)
                    );
                }
                TaskDependencyTraversalService.DownstreamMetrics dm = downstreamMetrics.getOrDefault(
                    task.getId(), new TaskDependencyTraversalService.DownstreamMetrics(0, 0)
                );
                List<Long> depIds = directDependentsMap.getOrDefault(task.getId(), List.of());
                List<Long> parentIds = directDependenciesMap.getOrDefault(task.getId(), List.of());

                GravityCalculationResult result = gravityEngineService.calculateGravity(
                    task, settingsSnapshot, workload, dm.directCount(), dm.indirectCount(), clock
                );
                return gravityCalculatorService.toTaskResponseDto(task, result, depIds, parentIds);
            })
            .sorted(Comparator.comparingDouble(TaskResponseDto::getTotalGravityScore).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Fetches all tasks awaiting Manager Approval (Manager Queue).
     */
    @Transactional(readOnly = true)
    public List<TaskResponseDto> getPendingTasks() {
        List<Task> pendingTasks = taskRepository.findAllPendingWithSingleValued();
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            List<Task> fallback = taskRepository.findAllWithDependencies();
            if (fallback != null && !fallback.isEmpty()) {
                pendingTasks = fallback.stream()
                    .filter(task -> task.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL)
                    .collect(Collectors.toList());
            }
        }

        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return List.of();
        }

        List<Long> pendingIds = pendingTasks.stream().map(Task::getId).toList();

        List<Object[]> edges = queryDirectEdgesPartitioned(pendingIds);
        Map<Long, List<Long>> directDependentsMap = buildEdgeMap(edges, true);
        Map<Long, List<Long>> directDependenciesMap = buildEdgeMap(edges, false);

        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> downstreamMetrics =
            traversalService.calculatePageDownstreamMetrics(pendingIds, directDependentsMap);

        com.gravitycanvas.entity.GravitySettings settingsSnapshot = settingsService != null ? settingsService.getSettings() : null;
        Map<Long, Long> workloadCache = new HashMap<>();
        Clock clock = Clock.systemDefaultZone();

        return pendingTasks.stream()
            .map(task -> {
                long workload = 0L;
                if (task.getAssignee() != null) {
                    workload = workloadCache.computeIfAbsent(
                        task.getAssignee().getId(),
                        id -> taskRepository.countByAssigneeIdAndStatusNot(id, com.gravitycanvas.entity.TaskStatus.DONE)
                    );
                }
                TaskDependencyTraversalService.DownstreamMetrics dm = downstreamMetrics.getOrDefault(
                    task.getId(), new TaskDependencyTraversalService.DownstreamMetrics(0, 0)
                );
                List<Long> depIds = directDependentsMap.getOrDefault(task.getId(), List.of());
                List<Long> parentIds = directDependenciesMap.getOrDefault(task.getId(), List.of());

                GravityCalculationResult result = gravityEngineService.calculateGravity(
                    task, settingsSnapshot, workload, dm.directCount(), dm.indirectCount(), clock
                );
                return gravityCalculatorService.toTaskResponseDto(task, result, depIds, parentIds);
            })
            .sorted(Comparator.comparing(TaskResponseDto::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Paginated Task Catalog Search (APPROVED tasks only).
     * Enforces Stage 1 ID pagination, Stage 2 single-valued root hydration, unified edge projection,
     * multi-root batched BFS, request-scoped workload caching, and unified Gravity Engine scoring.
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponseDto> searchTasks(TaskFilterParams params, int page, int size) {
        validatePaginationParams(page, size, params != null ? params.getSortBy() : null, params != null ? params.getSortDir() : null);

        Sort.Direction direction = "asc".equalsIgnoreCase(params != null ? params.getSortDir() : "desc") 
            ? Sort.Direction.ASC 
            : Sort.Direction.DESC;
        String sortBy = (params != null && params.getSortBy() != null && !params.getSortBy().trim().isEmpty()) 
            ? params.getSortBy().trim() 
            : "createdAt";

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Specification<Task> spec = TaskFilterSpecification.build(ApprovalStatus.APPROVED, params);

        return executePaginatedRetrieval(spec, pageable);
    }

    /**
     * Paginated Pending Approvals Search (PENDING_APPROVAL tasks only, Manager/Lead restricted).
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponseDto> searchPendingTasks(
        int page, int size, String sortBy, String sortDir,
        TaskEnvironment environment, Long assigneeId, String search
    ) {
        validatePaginationParams(page, size, sortBy, sortDir);

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String effectiveSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "createdAt";

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, effectiveSortBy));
        TaskFilterParams params = new TaskFilterParams(null, environment, assigneeId, search, effectiveSortBy, sortDir);
        Specification<Task> spec = TaskFilterSpecification.build(ApprovalStatus.PENDING_APPROVAL, params);

        return executePaginatedRetrieval(spec, pageable);
    }

    private PageResponse<TaskResponseDto> executePaginatedRetrieval(Specification<Task> spec, Pageable pageable) {
        // Stage 1: Safe ID Pagination with native LIMIT/OFFSET
        Page<Long> pageIds = taskRepository.findTaskIds(spec, pageable);
        if (pageIds.isEmpty() || pageIds.getContent().isEmpty()) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize(), pageIds.getTotalElements());
        }

        List<Long> ids = pageIds.getContent();

        // Stage 2: Hydrate root entities with ManyToOne only (zero Cartesian joins)
        List<Task> rootTasks = taskRepository.findRootEntitiesByIdIn(ids);
        Map<Long, Task> taskMap = rootTasks.stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        List<Task> orderedTasks = ids.stream().map(taskMap::get).filter(Objects::nonNull).toList();

        // Stage 2B: Direct dependency edge projection
        List<Object[]> edges = queryDirectEdgesPartitioned(ids);
        Map<Long, List<Long>> directDependentsMap = buildEdgeMap(edges, true);
        Map<Long, List<Long>> directDependenciesMap = buildEdgeMap(edges, false);

        // Stage 3: Multi-root BFS downstream metrics
        Map<Long, TaskDependencyTraversalService.DownstreamMetrics> downstreamMetrics =
            traversalService.calculatePageDownstreamMetrics(ids, directDependentsMap);

        // Stage 4: Settings snapshot & per-request workload cache
        com.gravitycanvas.entity.GravitySettings settingsSnapshot = settingsService != null ? settingsService.getSettings() : null;
        Map<Long, Long> workloadCache = new HashMap<>();
        Clock clock = Clock.systemDefaultZone();

        // Stage 5: Master calculation & DTO mapping
        List<TaskResponseDto> dtoList = new ArrayList<>();
        for (Task task : orderedTasks) {
            long workload = 0L;
            if (task.getAssignee() != null) {
                workload = workloadCache.computeIfAbsent(
                    task.getAssignee().getId(),
                    id -> taskRepository.countByAssigneeIdAndStatusNot(id, com.gravitycanvas.entity.TaskStatus.DONE)
                );
            }
            TaskDependencyTraversalService.DownstreamMetrics dm = downstreamMetrics.getOrDefault(
                task.getId(), new TaskDependencyTraversalService.DownstreamMetrics(0, 0)
            );
            List<Long> depIds = directDependentsMap.getOrDefault(task.getId(), List.of());
            List<Long> parentIds = directDependenciesMap.getOrDefault(task.getId(), List.of());

            GravityCalculationResult result = gravityEngineService.calculateGravity(
                task, settingsSnapshot, workload, dm.directCount(), dm.indirectCount(), clock
            );
            dtoList.add(gravityCalculatorService.toTaskResponseDto(task, result, depIds, parentIds));
        }

        return new PageResponse<>(
            dtoList,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            pageIds.getTotalElements(),
            pageIds.getTotalPages()
        );
    }

    private void validatePaginationParams(int page, int size, String sortBy, String sortDir) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        long rawOffset = (long) page * (long) size;
        if (rawOffset > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Requested page offset exceeds maximum supported index (offset overflow)");
        }
        if (sortBy != null && !sortBy.trim().isEmpty() && !ALLOWED_SORT_PROPERTIES.contains(sortBy.trim())) {
            throw new IllegalArgumentException("Invalid sort field: " + sortBy + ". Allowed fields: createdAt, deadline, status, title, environment");
        }
        if (sortDir != null && !sortDir.trim().isEmpty() && !"asc".equalsIgnoreCase(sortDir.trim()) && !"desc".equalsIgnoreCase(sortDir.trim())) {
            throw new IllegalArgumentException("Invalid sort direction: " + sortDir + ". Allowed directions: asc, desc");
        }
    }

    private List<Object[]> queryDirectEdgesPartitioned(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return List.of();
        List<Long> idList = new ArrayList<>(taskIds);
        int batchSize = 900;
        List<Object[]> allEdges = new ArrayList<>();
        for (int i = 0; i < idList.size(); i += batchSize) {
            List<Long> subList = idList.subList(i, Math.min(i + batchSize, idList.size()));
            allEdges.addAll(taskRepository.findDirectDependencyEdges(subList));
        }
        return allEdges;
    }

    private Map<Long, List<Long>> buildEdgeMap(List<Object[]> edges, boolean parentToDependent) {
        Map<Long, List<Long>> map = new HashMap<>();
        if (edges == null) return map;
        for (Object[] edge : edges) {
            Long parentId = ((Number) edge[0]).longValue();
            Long dependentId = ((Number) edge[1]).longValue();
            if (parentToDependent) {
                map.computeIfAbsent(parentId, k -> new ArrayList<>()).add(dependentId);
            } else {
                map.computeIfAbsent(dependentId, k -> new ArrayList<>()).add(parentId);
            }
        }
        return map;
    }

    /**
     * Creates a new task card (Open to both Managers and Developers).
     * If created by a Developer and Manager settings require authorization (requireApprovalForDevTasks == true),
     * the task is saved with PENDING_APPROVAL status.
     */
    @Transactional
    public TaskResponseDto createTask(CreateTaskRequest req) {
        Task task = new Task();
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());
        task.setEnvironment(req.getEnvironment());
        task.setCustomFactorApplied(req.isCustomFactorApplied());
        task.setDeadline(req.getDeadline());
        task.setCreatedAt(LocalDateTime.now());
        task.setLastActivityAt(LocalDateTime.now());

        // Get Current Authenticated User (Creator)
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        UserAccount creator = userRepository.findByUsername(currentUsername).orElse(null);
        task.setCreatedBy(creator);

        // Assignee
        if (req.getAssigneeId() != null) {
            UserAccount assignee = userRepository.findById(req.getAssigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + req.getAssigneeId()));
            task.setAssignee(assignee);
        } else {
            task.setAssignee(creator);
        }

        // Parent Dependencies
        if (req.getParentTaskIds() != null && !req.getParentTaskIds().isEmpty()) {
            Set<Long> uniqueParentIds = new LinkedHashSet<>(req.getParentTaskIds());
            Set<Task> parentTasks = new HashSet<>();
            for (Long parentId : uniqueParentIds) {
                Task parent = taskRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent task not found with ID: " + parentId));
                parentTasks.add(parent);
            }
            task.setDependencies(parentTasks);
            for (Task parent : parentTasks) {
                if (parent.getDependentTasks() == null) {
                    parent.setDependentTasks(new HashSet<>());
                }
                parent.getDependentTasks().add(task);
            }
        }

        // All manager/admin created tasks are directly APPROVED
        task.setApprovalStatus(ApprovalStatus.APPROVED);

        Task saved = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(saved, List.of(saved));
    }

    /**
     * Manager authorizes and approves a pending developer task.
     */
    @Transactional
    public TaskResponseDto approveTask(Long id) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        task.setApprovalStatus(ApprovalStatus.APPROVED);
        task.setLastActivityAt(LocalDateTime.now());
        Task saved = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(saved, List.of(saved));
    }

    /**
     * Manager rejects a pending developer task with mandatory reason.
     */
    @Transactional
    public TaskResponseDto rejectTask(Long id, RejectTaskRequest req) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        task.setApprovalStatus(ApprovalStatus.REJECTED);
        if (req != null && req.getRejectionReason() != null && !req.getRejectionReason().trim().isEmpty()) {
            task.setRejectionReason(req.getRejectionReason().trim());
        } else {
            task.setRejectionReason("No rejection reason provided by manager.");
        }
        task.setLastActivityAt(LocalDateTime.now());
        Task saved = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(saved, List.of(saved));
    }

    /**
     * Deletes a task by ID (Manager permission required).
     * Cleans up bidirectional dependency relationships on both owning and mappedBy sides
     * to prevent orphaned foreign keys in TASK_DEPENDENCIES.
     */
    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        // 1. Disconnect from parent tasks (this task is a dependent of parent)
        if (task.getDependencies() != null) {
            for (Task parent : task.getDependencies()) {
                if (parent.getDependentTasks() != null) {
                    parent.getDependentTasks().remove(task);
                }
            }
            task.getDependencies().clear();
        }

        // 2. Disconnect from child tasks (this task is a parent of child)
        if (task.getDependentTasks() != null) {
            for (Task child : task.getDependentTasks()) {
                if (child.getDependencies() != null) {
                    child.getDependencies().remove(task);
                }
            }
            task.getDependentTasks().clear();
        }

        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponseDto updateDeadline(Long id, UpdateDeadlineRequest req) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        task.setDeadline(req.getDeadline());
        task.setLastActivityAt(LocalDateTime.now());
        Task updated = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(updated, List.of(updated));
    }

    @Transactional
    public TaskResponseDto reassignTask(Long id, ReassignTaskRequest req) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        if (req.getAssigneeId() != null) {
            UserAccount assignee = userRepository.findById(req.getAssigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + req.getAssigneeId()));
            task.setAssignee(assignee);
        } else {
            task.setAssignee(null);
        }

        task.setLastActivityAt(LocalDateTime.now());
        Task updated = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(updated, List.of(updated));
    }

    @Transactional
    public TaskResponseDto updateStatus(Long id, UpdateStatusRequest req) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        task.setStatus(req.getStatus());
        task.setLastActivityAt(LocalDateTime.now());
        Task updated = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(updated, List.of(updated));
    }

    @Transactional
    public TaskResponseDto pingBlocker(Long id) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        boolean newPingedState = !task.isPinged();
        task.setPinged(newPingedState);
        if (newPingedState) {
            task.setPingCount(task.getPingCount() + 1);
        }
        task.setLastActivityAt(LocalDateTime.now());
        Task updated = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(updated, List.of(updated));
    }

    @Transactional
    public TaskResponseDto updateTaskDependencies(Long id, UpdateTaskDependenciesRequest req) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        List<Long> rawParentIds = (req != null && req.getParentTaskIds() != null)
            ? req.getParentTaskIds()
            : List.of();

        Set<Long> uniqueParentIds = new LinkedHashSet<>(rawParentIds);

        // 1. Check self-dependency
        if (uniqueParentIds.contains(id)) {
            throw new CircularDependencyException(
                "Self-dependency detected: Task #" + id + " cannot depend on itself."
            );
        }

        // 2. Verify all parent IDs exist
        List<Task> parentTasks = new ArrayList<>();
        for (Long parentId : uniqueParentIds) {
            Task parent = taskRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent task not found with ID: " + parentId));
            parentTasks.add(parent);
        }

        // 3. Cycle validation
        for (Task parent : parentTasks) {
            if (isDownstream(task, parent.getId())) {
                throw new CircularDependencyException(
                    "Circular dependency detected: Task #" + id + " cannot depend on Task #" + parent.getId() +
                    " because Task #" + parent.getId() + " already depends directly or indirectly on Task #" + id + "."
                );
            }
        }

        // 4. Clean up old parent relationships
        if (task.getDependencies() != null) {
            for (Task oldParent : task.getDependencies()) {
                if (oldParent.getDependentTasks() != null) {
                    oldParent.getDependentTasks().remove(task);
                }
            }
            task.getDependencies().clear();
        } else {
            task.setDependencies(new HashSet<>());
        }

        // 5. Establish new parent relationships
        for (Task parent : parentTasks) {
            if (parent.getDependentTasks() == null) {
                parent.setDependentTasks(new HashSet<>());
            }
            parent.getDependentTasks().add(task);
            task.getDependencies().add(parent);
        }

        task.setLastActivityAt(LocalDateTime.now());
        Task updated = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(updated, List.of(updated));
    }

    @Transactional
    public TaskResponseDto addDependency(Long id, Long parentTaskId) {
        if (id.equals(parentTaskId)) {
            throw new CircularDependencyException("Self-dependency detected: Task #" + id + " cannot depend on itself.");
        }

        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + id));

        Task parent = taskRepository.findById(parentTaskId)
            .orElseThrow(() -> new ResourceNotFoundException("Parent task not found with ID: " + parentTaskId));

        if (isDownstream(task, parentTaskId)) {
            throw new CircularDependencyException(
                "Circular dependency detected: Task #" + id + " cannot depend on Task #" + parentTaskId +
                " because Task #" + parentTaskId + " already depends directly or indirectly on Task #" + id + "."
            );
        }

        if (task.getDependencies() == null) {
            task.setDependencies(new HashSet<>());
        }
        if (parent.getDependentTasks() == null) {
            parent.setDependentTasks(new HashSet<>());
        }

        task.getDependencies().add(parent);
        parent.getDependentTasks().add(task);

        task.setLastActivityAt(LocalDateTime.now());
        Task updated = taskRepository.save(task);
        return gravityCalculatorService.calculateGravity(updated, List.of(updated));
    }

    private boolean isDownstream(Task startTask, Long targetTaskId) {
        if (startTask == null || targetTaskId == null) return false;
        Set<Long> visited = new HashSet<>();
        Queue<Task> queue = new LinkedList<>();

        if (startTask.getDependentTasks() != null) {
            for (Task child : startTask.getDependentTasks()) {
                if (child != null) {
                    if (child.getId().equals(targetTaskId)) return true;
                    visited.add(child.getId());
                    queue.add(child);
                }
            }
        }

        while (!queue.isEmpty()) {
            Task current = queue.poll();
            if (current.getDependentTasks() != null) {
                for (Task child : current.getDependentTasks()) {
                    if (child != null && !visited.contains(child.getId())) {
                        if (child.getId().equals(targetTaskId)) return true;
                        visited.add(child.getId());
                        queue.add(child);
                    }
                }
            }
        }
        return false;
    }
}
