package com.gravitycanvas.repository;

import com.gravitycanvas.entity.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public class TaskRepositoryCustomImpl implements TaskRepositoryCustom {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
        "createdAt", "deadline", "status", "title", "environment"
    );

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Long> findTaskIds(Specification<Task> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // -------------------------------------------------------------
        // 1. COUNT QUERY: Exact same predicate, zero collection joins
        // -------------------------------------------------------------
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Task> countRoot = countQuery.from(Task.class);
        countQuery.select(cb.count(countRoot));

        if (spec != null) {
            Predicate countPredicate = spec.toPredicate(countRoot, countQuery, cb);
            if (countPredicate != null) {
                countQuery.where(countPredicate);
            }
        }
        Long totalElements = entityManager.createQuery(countQuery).getSingleResult();

        // -------------------------------------------------------------
        // 2. OFFSET VALIDATION & BOUNDARY GUARDS
        // -------------------------------------------------------------
        long offset = pageable.getOffset();
        if (offset > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Requested page offset exceeds maximum supported limit (offset overflow)");
        }
        if (totalElements == 0 || offset >= totalElements) {
            return new PageImpl<>(List.of(), pageable, totalElements);
        }

        // -------------------------------------------------------------
        // 3. ID QUERY: Selects only t.id with native LIMIT and OFFSET
        // -------------------------------------------------------------
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<Task> root = idQuery.from(Task.class);
        idQuery.select(root.get("id"));

        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, idQuery, cb);
            if (predicate != null) {
                idQuery.where(predicate);
            }
        }

        // Apply allowlisted sorting with deterministic ID tie-breaker
        List<Order> orders = new ArrayList<>();
        boolean primaryAscending = false;
        if (pageable.getSort().isSorted()) {
            for (Sort.Order sortOrder : pageable.getSort()) {
                String property = sortOrder.getProperty();
                if (ALLOWED_SORT_PROPERTIES.contains(property)) {
                    Path<?> sortPath = root.get(property);
                    boolean isAsc = sortOrder.isAscending();
                    orders.add(isAsc ? cb.asc(sortPath) : cb.desc(sortPath));
                    primaryAscending = isAsc;
                }
            }
        } else {
            // Default sort: createdAt DESC
            orders.add(cb.desc(root.get("createdAt")));
        }

        // Deterministic secondary tie-breaker on task ID
        orders.add(primaryAscending ? cb.asc(root.get("id")) : cb.desc(root.get("id")));
        idQuery.orderBy(orders);

        TypedQuery<Long> typedIdQuery = entityManager.createQuery(idQuery);
        typedIdQuery.setFirstResult((int) offset);
        typedIdQuery.setMaxResults(pageable.getPageSize());

        List<Long> ids = typedIdQuery.getResultList();
        return new PageImpl<>(ids, pageable, totalElements);
    }
}
