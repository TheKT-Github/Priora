package com.gravitycanvas.service;

import com.gravitycanvas.dto.TaskResponseDto;
import com.gravitycanvas.dto.UserDto;
import com.gravitycanvas.entity.GravitySettings;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter Service bridging GravityEngineService with TaskResponseDto for REST API responses.
 * Maps master GravityCalculationResult metrics onto TaskResponseDto without formula duplication.
 */
@Service
public class GravityCalculatorService {

    private final GravityEngineService gravityEngineService;
    private final TaskRepository taskRepository;
    private final SettingsService settingsService;
    private final TaskDependencyTraversalService traversalService;

    public GravityCalculatorService(GravityEngineService gravityEngineService,
                                    TaskRepository taskRepository,
                                    SettingsService settingsService) {
        this(gravityEngineService, taskRepository, settingsService, new TaskDependencyTraversalService());
    }

    @Autowired
    public GravityCalculatorService(GravityEngineService gravityEngineService,
                                    TaskRepository taskRepository,
                                    SettingsService settingsService,
                                    TaskDependencyTraversalService traversalService) {
        this.gravityEngineService = gravityEngineService;
        this.taskRepository = taskRepository;
        this.settingsService = settingsService;
        this.traversalService = traversalService != null ? traversalService : new TaskDependencyTraversalService();
    }

    /**
     * Pure DTO mapping method. Populates base attributes and maps all metrics directly from
     * the verified GravityCalculationResult. Zero mathematical formula duplication.
     */
    public TaskResponseDto toTaskResponseDto(
        Task task,
        GravityCalculationResult calc,
        List<Long> directDependentIds,
        List<Long> directDependencyIds
    ) {
        TaskResponseDto dto = new TaskResponseDto();

        // 1. Copy Basic Task Attributes
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        dto.setApprovalStatus(task.getApprovalStatus());
        dto.setRejectionReason(task.getRejectionReason());
        dto.setEnvironment(task.getEnvironment());
        dto.setCustomFactorApplied(task.isCustomFactorApplied());
        dto.setPinged(task.isPinged());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setLastActivityAt(task.getLastActivityAt());
        dto.setDeadline(task.getDeadline());
        dto.setPingCount(task.getPingCount());
        dto.setVersion(task.getVersion());

        // Assignee Details
        if (task.getAssignee() != null) {
            dto.setAssignee(new UserDto(
                task.getAssignee().getId(),
                task.getAssignee().getUsername(),
                task.getAssignee().getFullName(),
                task.getAssignee().getRole(),
                task.getAssignee().getEmployeeNo(),
                task.getAssignee().getDepartment()
            ));
        }

        // Creator Details
        if (task.getCreatedBy() != null) {
            dto.setCreatedBy(new UserDto(
                task.getCreatedBy().getId(),
                task.getCreatedBy().getUsername(),
                task.getCreatedBy().getFullName(),
                task.getCreatedBy().getRole(),
                task.getCreatedBy().getEmployeeNo(),
                task.getCreatedBy().getDepartment()
            ));
        }

        // Dependency IDs
        dto.setDependentTaskIds(directDependentIds != null ? directDependentIds : List.of());
        dto.setDependencyIds(directDependencyIds != null ? directDependencyIds : List.of());

        // Map calculation metrics from master result
        if (calc != null) {
            dto.setDirectBlockedCount(calc.directCount());
            dto.setIndirectBlockedCount(calc.indirectCount());
            dto.setDependencyScore(calc.dependencyScore());

            dto.setDeadlineProximityScore(calc.deadlineProximityScore());
            dto.setHoursUntilDeadline(calc.hoursUntilDeadline());
            dto.setOverdue(calc.overdue());

            dto.setStagnationScore(calc.stagnationScore());
            dto.setHoursInactive(calc.hoursInactive());

            dto.setAssigneeActiveTasksCount(calc.assigneeActiveTasksCount());
            dto.setWorkloadDensityScore(calc.workloadDensityScore());

            dto.setCustomFactorName(calc.customFactorName());
            dto.setCustomFactorScore(calc.customFactorScore());

            dto.setEnvironmentMultiplier(calc.environmentMultiplier());
            dto.setTotalGravityScore(calc.totalGravityScore());
            dto.setCanvasZone(calc.canvasZone());
        }

        return dto;
    }

    /**
     * Existing 2-argument method: delegates to master calculateGravity in GravityEngineService
     * and maps via toTaskResponseDto. Preserved for backward compatibility.
     */
    public TaskResponseDto calculateGravity(Task task, List<Task> allTasks) {
        GravitySettings settings = settingsService != null ? settingsService.getSettings() : null;
        long activeAssigneeTasks = 0;
        if (task.getAssignee() != null && taskRepository != null) {
            activeAssigneeTasks = taskRepository.countByAssigneeIdAndStatusNot(
                task.getAssignee().getId(), TaskStatus.DONE
            );
        }

        int directCount = task.getDependentTasks() != null ? task.getDependentTasks().size() : 0;
        int indirectCount = traversalService.calculateIndirectDownstreamCount(task);

        List<Long> directDependentIds = task.getDependentTasks() != null 
            ? task.getDependentTasks().stream().map(Task::getId).collect(Collectors.toList()) 
            : List.of();
        List<Long> directDependencyIds = task.getDependencies() != null 
            ? task.getDependencies().stream().map(Task::getId).collect(Collectors.toList()) 
            : List.of();

        GravityCalculationResult calcResult = gravityEngineService.calculateGravity(
            task, settings, activeAssigneeTasks, directCount, indirectCount, Clock.systemDefaultZone()
        );

        return toTaskResponseDto(task, calcResult, directDependentIds, directDependencyIds);
    }
}
