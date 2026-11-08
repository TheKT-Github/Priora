-- ===================================================================
-- PRIORA PAGINATION & ORDERING INDEXES MIGRATION (V3)
-- Standard transactional DDL compatible with PostgreSQL and H2
-- ===================================================================

-- Composite Index covering Stage 1 root ID pagination and deterministic ordering:
-- Covers: WHERE approval_status = 'APPROVED' ORDER BY created_at DESC, id DESC
-- Covers: WHERE approval_status = 'PENDING_APPROVAL' ORDER BY created_at DESC, id DESC
CREATE INDEX idx_tasks_approval_created_id
    ON TASKS (APPROVAL_STATUS, CREATED_AT DESC, ID DESC);
