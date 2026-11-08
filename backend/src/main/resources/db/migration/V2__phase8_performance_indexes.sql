-- ===================================================================
-- PRIORA PERFORMANCE INDEXES MIGRATION (V2)
-- Standard transactional DDL compatible with PostgreSQL and H2
-- ===================================================================

-- 1. Composite Index for Workload Density queries:
-- Covers: taskRepository.countByAssigneeIdAndStatusNot(assigneeId, DONE)
CREATE INDEX idx_tasks_assignee_status
    ON TASKS (ASSIGNEE_ID, STATUS);

-- 2. Index for inverse dependency lookups:
-- Covers: task.dependencies reverse lookup (WHERE DEPENDENT_TASK_ID = ?)
CREATE INDEX idx_task_deps_dependent_id
    ON TASK_DEPENDENCIES (DEPENDENT_TASK_ID);
