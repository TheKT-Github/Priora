package com.gravitycanvas.repository;

import com.gravitycanvas.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface TaskRepositoryCustom {
    /**
     * Executes Stage 1 of safe pagination: queries root task IDs matching specification filters
     * with native SQL LIMIT and OFFSET, applying allowlisted sorting and deterministic tie-breaking.
     * Does NOT join any collections, preventing Hibernate HHH000104 in-memory pagination warnings.
     */
    Page<Long> findTaskIds(Specification<Task> spec, Pageable pageable);
}
