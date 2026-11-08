package com.gravitycanvas.repository;

import com.gravitycanvas.dto.TaskFilterParams;
import com.gravitycanvas.entity.ApprovalStatus;
import com.gravitycanvas.entity.Task;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class TaskFilterSpecification {

    public static Specification<Task> build(ApprovalStatus requiredApprovalStatus, TaskFilterParams params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory approval status scope (e.g., APPROVED for catalog, PENDING_APPROVAL for queue)
            if (requiredApprovalStatus != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), requiredApprovalStatus));
            }

            if (params != null) {
                // 2. Status filter
                if (params.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), params.getStatus()));
                }

                // 3. Environment filter
                if (params.getEnvironment() != null) {
                    predicates.add(cb.equal(root.get("environment"), params.getEnvironment()));
                }

                // 4. Assignee filter (filters by foreign key without Cartesian join)
                if (params.getAssigneeId() != null) {
                    predicates.add(cb.equal(root.get("assignee").get("id"), params.getAssigneeId()));
                }

                // 5. Case-insensitive substring title search
                if (params.getSearch() != null && !params.getSearch().trim().isEmpty()) {
                    String pattern = "%" + params.getSearch().trim().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("title")), pattern));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
