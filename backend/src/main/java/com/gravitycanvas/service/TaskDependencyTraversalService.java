package com.gravitycanvas.service;

import com.gravitycanvas.entity.Task;
import com.gravitycanvas.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Dedicated Service for traversing the directed acyclic graph (DAG) of task dependencies.
 * Provides unified downstream reachability and indirect blast-radius counting via BFS.
 */
@Service
public class TaskDependencyTraversalService {

    public record DownstreamMetrics(int directCount, int indirectCount) {}

    public record Pair<A, B>(A rootId, B nodeId) {}

    private final TaskRepository taskRepository;

    public TaskDependencyTraversalService() {
        this.taskRepository = null;
    }

    @Autowired
    public TaskDependencyTraversalService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Multi-root BFS downstream reachability calculator with outgoing adjacency caching
     * and safe database parameter partitioning.
     * Evaluates direct and indirect downstream counts independently for every root task.
     *
     * @param pageRootIds List of task IDs to evaluate
     * @param initialDirectEdges Map of parent ID to direct dependent IDs seeded from initial edge fetch
     * @return Map of root task ID to DownstreamMetrics
     */
    public Map<Long, DownstreamMetrics> calculatePageDownstreamMetrics(
        List<Long> pageRootIds,
        Map<Long, List<Long>> initialDirectEdges
    ) {
        if (pageRootIds == null || pageRootIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<Long>> directMap = new HashMap<>();
        Map<Long, Set<Long>> reachableMap = new HashMap<>();
        for (Long r : pageRootIds) {
            directMap.put(r, new HashSet<>());
            reachableMap.put(r, new HashSet<>());
        }

        Map<Long, List<Long>> adjacencyCache = new HashMap<>();
        Set<Long> fetchedInDb = new HashSet<>();
        Queue<Pair<Long, Long>> workQueue = new ArrayDeque<>();

        // -------------------------------------------------------------
        // STEP 1: Process Direct Outbound Edges for All Page Roots
        // -------------------------------------------------------------
        if (initialDirectEdges != null) {
            for (Map.Entry<Long, List<Long>> entry : initialDirectEdges.entrySet()) {
                adjacencyCache.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                fetchedInDb.add(entry.getKey());
            }
        }

        for (Long r : pageRootIds) {
            fetchedInDb.add(r);
            adjacencyCache.putIfAbsent(r, new ArrayList<>());
            List<Long> directChildren = adjacencyCache.get(r);

            for (Long child : directChildren) {
                // Guard against self-edge: a task cannot depend on itself
                if (child.equals(r)) {
                    continue;
                }
                directMap.get(r).add(child);
                reachableMap.get(r).add(child);
                workQueue.add(new Pair<>(r, child));
            }
        }

        // -------------------------------------------------------------
        // STEP 2: Multi-Root Breadth-First Propagation Loop
        // -------------------------------------------------------------
        while (!workQueue.isEmpty()) {
            // Collect un-fetched node IDs currently in the queue
            Set<Long> needDbFetch = new HashSet<>();
            for (Pair<Long, Long> pair : workQueue) {
                Long target = pair.nodeId();
                if (!fetchedInDb.contains(target)) {
                    needDbFetch.add(target);
                }
            }

            // Batch query DB only for un-fetched nodes, partitioned for DB parameter limits
            if (!needDbFetch.isEmpty() && taskRepository != null) {
                List<Long> toFetchList = new ArrayList<>(needDbFetch);
                int batchSize = 900; // Safe partition size for PostgreSQL / H2 / Oracle parameter limits
                for (int i = 0; i < toFetchList.size(); i += batchSize) {
                    List<Long> subList = toFetchList.subList(i, Math.min(i + batchSize, toFetchList.size()));
                    List<Object[]> nextEdges = taskRepository.findDownstreamEdgesByParentIds(subList);
                    for (Long p : subList) {
                        adjacencyCache.putIfAbsent(p, new ArrayList<>());
                    }
                    for (Object[] edge : nextEdges) {
                        Long p = ((Number) edge[0]).longValue();
                        Long d = ((Number) edge[1]).longValue();
                        if (!p.equals(d)) {
                            adjacencyCache.get(p).add(d);
                        }
                    }
                }
                fetchedInDb.addAll(needDbFetch);
            }

            // Propagate reachability in-memory for the current wave
            int currentWaveSize = workQueue.size();
            for (int i = 0; i < currentWaveSize; i++) {
                Pair<Long, Long> pair = workQueue.poll();
                Long rootId = pair.rootId();
                Long currentNode = pair.nodeId();

                List<Long> children = adjacencyCache.getOrDefault(currentNode, List.of());
                for (Long child : children) {
                    // Guard: path returning to origin root is not a descendant of root
                    if (child.equals(rootId)) {
                        continue;
                    }
                    // Only enqueue if child is newly discovered for this root
                    if (reachableMap.get(rootId).add(child)) {
                        workQueue.add(new Pair<>(rootId, child));
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // STEP 3: Independent Metric Derivation per Root
        // -------------------------------------------------------------
        Map<Long, DownstreamMetrics> result = new HashMap<>();
        for (Long r : pageRootIds) {
            int direct = directMap.get(r).size();
            int indirect = Math.max(0, reachableMap.get(r).size() - direct);
            result.put(r, new DownstreamMetrics(direct, indirect));
        }
        return result;
    }

    /**
     * Calculates the number of indirect downstream tasks dependent on the start task using BFS on loaded entities.
     * Preserved for backward compatibility with existing callers.
     * Edge direction: Parent -> Dependent (downstream).
     *
     * @param startTask Starting task node
     * @return Total reachable unique downstream tasks minus direct dependents (indirect count)
     */
    public int calculateIndirectDownstreamCount(Task startTask) {
        if (startTask == null || startTask.getDependentTasks() == null || startTask.getDependentTasks().isEmpty()) {
            return 0;
        }

        Set<Task> visited = new HashSet<>();
        Queue<Task> queue = new LinkedList<>();

        for (Task directDep : startTask.getDependentTasks()) {
            if (directDep != null && !directDep.equals(startTask)) {
                visited.add(directDep);
                queue.add(directDep);
            }
        }

        int directCount = visited.size();

        while (!queue.isEmpty()) {
            Task current = queue.poll();
            if (current.getDependentTasks() != null) {
                for (Task nextDownstream : current.getDependentTasks()) {
                    if (nextDownstream != null && !nextDownstream.equals(startTask) && !visited.contains(nextDownstream)) {
                        visited.add(nextDownstream);
                        queue.add(nextDownstream);
                    }
                }
            }
        }

        return Math.max(0, visited.size() - directCount);
    }
}
