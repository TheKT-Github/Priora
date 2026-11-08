-- ===================================================================
-- PRIORA PHASE 8 — DATABASE PERFORMANCE & HIBERNATE OPTIMIZATION
-- Index Migration Reference Script (Production PostgreSQL / Oracle / H2)
-- ===================================================================
-- NOTE FOR PRODUCTION DEPLOYMENTS:
-- Priora production runs with spring.jpa.hibernate.ddl-auto=validate.
-- Therefore, Hibernate will NOT create or modify indexes automatically in production.
-- This script must be executed manually by the DBA against the production database.
--
-- IMPORTANT POSTGRESQL REQUIREMENT:
-- CREATE INDEX CONCURRENTLY cannot be executed inside a multi-statement transaction block
-- (i.e. outside of BEGIN ... COMMIT). Run this script in autocommit mode (e.g. psql default)
-- to avoid locking the TASKS or TASK_DEPENDENCIES tables during index construction.
-- ===================================================================

-- 1. Composite Index on TASKS (ASSIGNEE_ID, STATUS)
-- Query covered:
--   taskRepository.countByAssigneeIdAndStatusNot(assigneeId, DONE)
-- Rationale:
--   This is the single most frequently executed query in the system, executed once per task
--   during every Gravity score calculation to compute the Workload Density Score.
--   A composite index on (ASSIGNEE_ID, STATUS) allows PostgreSQL/Oracle/H2 to navigate the B-Tree
--   directly to ASSIGNEE_ID and evaluate the STATUS filter within that slice in a single index scan.
--   This is strictly superior to separate single-column indexes on ASSIGNEE_ID and STATUS, which
--   would require a Bitmap Index Scan merge or index scan followed by table heap fetch.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tasks_assignee_status
    ON TASKS (ASSIGNEE_ID, STATUS);

-- 2. Index on TASK_DEPENDENCIES (DEPENDENT_TASK_ID)
-- Query covered:
--   Loading task.dependencies (the mappedBy inverse side of the ManyToMany relationship):
--   SELECT ... FROM TASK_DEPENDENCIES WHERE DEPENDENT_TASK_ID = ?
-- Rationale:
--   The composite primary key on TASK_DEPENDENCIES is (PARENT_TASK_ID, DEPENDENT_TASK_ID).
--   In B-Tree indexing, the composite PK only covers lookups where PARENT_TASK_ID is the leading column
--   (i.e. loading task.dependentTasks).
--   Reverse lookups (finding parents of a dependent task) query on DEPENDENT_TASK_ID, which is the
--   trailing column and CANNOT use the primary key index efficiently in PostgreSQL/Oracle.
--   PostgreSQL does NOT auto-index foreign keys. This index fills that critical gap.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_deps_dependent_id
    ON TASK_DEPENDENCIES (DEPENDENT_TASK_ID);

-- ===================================================================
-- INDEXES EVALUATED AND DELIBERATELY OMITTED:
-- ===================================================================
-- 1. TASKS (APPROVAL_STATUS):
--    OMITTED: getAllTasks() and getPendingTasks() currently retrieve tasks and filter
--    approval status in-memory (.filter(t -> t.getApprovalStatus() == ...)).
--    Until query-level filtering (WHERE APPROVAL_STATUS = ?) is introduced in a future phase,
--    a database index on APPROVAL_STATUS provides 0 query execution benefit and would only
--    add write overhead on task inserts and approval status updates.
--
-- 2. USERS (USERNAME):
--    OMITTED (ALREADY PRESENT): UserAccount.java declares username with @Column(unique = true).
--    JPA / Hibernate automatically generates a UNIQUE constraint on USERS.USERNAME,
--    which both PostgreSQL and H2 back with an automatic unique B-Tree index.
--    Adding an explicit index would create an unnecessary duplicate index.
--
-- 3. TASKS (CREATED_BY_ID):
--    OMITTED: CREATED_BY_ID is never used as a predicate in any query (no findByCreatedBy).
--    It is only loaded via EAGER JoinColumn on task retrieval. An index provides no query benefit.
-- ===================================================================
