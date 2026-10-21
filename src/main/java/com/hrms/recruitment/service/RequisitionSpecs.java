package com.hrms.recruitment.service;

import com.hrms.recruitment.entity.JobRequisition;
import com.hrms.recruitment.enums.RequisitionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Locale;

/** Dynamic filters for the requisition list. Every value reaches the database as a bind parameter. */
public final class RequisitionSpecs {

    private RequisitionSpecs() {}

    public static Specification<JobRequisition> matching(RequisitionStatus status, Long departmentId, String q) {
        Specification<JobRequisition> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (departmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("departmentId"), departmentId));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), like, '\\'),
                cb.like(cb.lower(root.get("jobTitle")), like, '\\')));
        }
        return spec;
    }

    /** What a non-HR caller may see: what they raised, plus anything in a department they head. */
    public static Specification<JobRequisition> visibleTo(Long employeeId, Collection<Long> headedDepartmentIds) {
        return (root, query, cb) -> headedDepartmentIds.isEmpty()
            ? cb.equal(root.get("requestedBy"), employeeId)
            : cb.or(cb.equal(root.get("requestedBy"), employeeId), root.get("departmentId").in(headedDepartmentIds));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
