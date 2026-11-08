package com.gravitycanvas.repository;

import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task>, TaskRepositoryCustom {

    /**
     * Counts active tasks for a specific user (status != DONE).
     * Used by GravityCalculatorService for calculating Workload Density Score.
     * The composite index idx_tasks_assignee_status on (ASSIGNEE_ID, STATUS) covers this predicate.
     */
    long countByAssigneeIdAndStatusNot(Long assigneeId, TaskStatus status);

    /**
     * Loads all tasks with both ManyToMany dependency collections (dependentTasks, dependencies)
     * and ManyToOne user associations (assignee, createdBy) eagerly populated via a single
     * LEFT JOIN query. Preserved for Phase 8 integration tests.
     */
    @EntityGraph(attributePaths = {"dependentTasks", "dependencies", "assignee", "createdBy"})
    @Query("SELECT DISTINCT t FROM Task t")
    List<Task> findAllWithDependencies();

    /**
     * Loads all APPROVED tasks with single-valued associations (assignee, createdBy) fetched eagerly.
     * Dual ManyToMany collections (dependentTasks, dependencies) are NOT joined, eliminating
     * Cartesian row multiplication and memory deduplication overhead.
     */
    @Query("SELECT t FROM Task t " +
           "LEFT JOIN FETCH t.assignee " +
           "LEFT JOIN FETCH t.createdBy " +
           "WHERE t.approvalStatus = com.gravitycanvas.entity.ApprovalStatus.APPROVED")
    List<Task> findAllApprovedWithSingleValued();

    /**
     * Loads all PENDING_APPROVAL tasks with single-valued associations fetched eagerly.
     */
    @Query("SELECT t FROM Task t " +
           "LEFT JOIN FETCH t.assignee " +
           "LEFT JOIN FETCH t.createdBy " +
           "WHERE t.approvalStatus = com.gravitycanvas.entity.ApprovalStatus.PENDING_APPROVAL")
    List<Task> findAllPendingWithSingleValued();

    /**
     * Hydrates root tasks matching specified IDs with only single-valued associations.
     * Zero collection joins, preventing Cartesian expansion in Stage 2 pagination.
     */
    @Query("SELECT t FROM Task t " +
           "LEFT JOIN FETCH t.assignee " +
           "LEFT JOIN FETCH t.createdBy " +
           "WHERE t.id IN (:ids)")
    List<Task> findRootEntitiesByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Direct dependency edge projection for a collection of task IDs directly from TASK_DEPENDENCIES.
     * Retrieves both upstream parents and downstream dependents in a single SQL operation.
     */
    @Query(value = "SELECT PARENT_TASK_ID, DEPENDENT_TASK_ID FROM TASK_DEPENDENCIES " +
                   "WHERE PARENT_TASK_ID IN (:taskIds) OR DEPENDENT_TASK_ID IN (:taskIds)",
           nativeQuery = true)
    List<Object[]> findDirectDependencyEdges(@Param("taskIds") Collection<Long> taskIds);

    /**
     * Batched lookup of downstream dependents for a collection of parent IDs.
     * Used by multi-root BFS traversal waves.
     */
    @Query(value = "SELECT PARENT_TASK_ID, DEPENDENT_TASK_ID FROM TASK_DEPENDENCIES " +
                   "WHERE PARENT_TASK_ID IN (:parentIds)",
           nativeQuery = true)
    List<Object[]> findDownstreamEdgesByParentIds(@Param("parentIds") Collection<Long> parentIds);
}
