package com.hrms.employee.repository.specification;

import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.dto.request.EmployeeFilterRequest;
import com.hrms.employee.entity.Employee;
import jakarta.persistence.criteria.*;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Criteria API specifications for dynamic {@link Employee} filtering.
 *
 * <p>Supports the Angular Material table's server-side filter panel by composing
 * individual predicates into a single {@link Specification} via logical AND.
 * Each predicate is only added when its corresponding filter field is non-null,
 * so unused filter fields are transparently ignored.</p>
 *
 * <p>Usage in service layer:
 * <pre>{@code
 *   Specification<Employee> spec = EmployeeSpecification.from(filterRequest);
 *   Page<Employee> page = employeeRepository.findAll(spec, pageable);
 * }</pre>
 * </p>
 */
@UtilityClass
public class EmployeeSpecification {

    /**
     * Builds a composite {@link Specification} from a {@link EmployeeFilterRequest}.
     * All non-null filter fields are combined with logical AND.
     */
    public static Specification<Employee> from(EmployeeFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── isActive filter ─────────────────────────────────────────────
            if (filter.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), filter.getIsActive()));
            }

            // ── Keyword search (firstName, lastName, employeeCode, workEmail) ─
            if (StringUtils.hasText(filter.getKeyword())) {
                String pattern = "%" + filter.getKeyword().toLowerCase() + "%";
                Predicate keywordPredicate = cb.or(
                    cb.like(cb.lower(root.get("firstName")),    pattern),
                    cb.like(cb.lower(root.get("lastName")),     pattern),
                    cb.like(cb.lower(root.get("employeeCode")), pattern),
                    cb.like(cb.lower(root.get("workEmail")),    pattern),
                    cb.like(cb.lower(root.get("middleName")),   pattern)
                );
                predicates.add(keywordPredicate);
            }

            // ── Enum filters ─────────────────────────────────────────────────
            if (filter.getGender() != null) {
                predicates.add(cb.equal(root.get("gender"), filter.getGender()));
            }

            if (filter.getEmploymentStatus() != null) {
                predicates.add(cb.equal(
                    root.get("employmentStatus"), filter.getEmploymentStatus()
                ));
            }

            if (filter.getEmploymentType() != null) {
                predicates.add(cb.equal(
                    root.get("employmentType"), filter.getEmploymentType()
                ));
            }

            // ── Nationality filter ───────────────────────────────────────────
            if (StringUtils.hasText(filter.getNationality())) {
                predicates.add(cb.like(
                    cb.lower(root.get("nationality")),
                    "%" + filter.getNationality().toLowerCase() + "%"
                ));
            }

            // ── Hire date range ──────────────────────────────────────────────
            if (filter.getHireDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("hireDate"), filter.getHireDateFrom()
                ));
            }
            if (filter.getHireDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("hireDate"), filter.getHireDateTo()
                ));
            }

            // ── Date of birth range ──────────────────────────────────────────
            if (filter.getDateOfBirthFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("dateOfBirth"), filter.getDateOfBirthFrom()
                ));
            }
            if (filter.getDateOfBirthTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("dateOfBirth"), filter.getDateOfBirthTo()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reusable individual specifications (for programmatic composition)
    // ──────────────────────────────────────────────────────────────────────────

    public static Specification<Employee> isActive() {
        return (root, query, cb) -> cb.equal(root.get("isActive"), true);
    }

    public static Specification<Employee> isInactive() {
        return (root, query, cb) -> cb.equal(root.get("isActive"), false);
    }

    public static Specification<Employee> hasStatus(EmploymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("employmentStatus"), status);
    }

    public static Specification<Employee> hasType(EmploymentType type) {
        return (root, query, cb) -> cb.equal(root.get("employmentType"), type);
    }

    public static Specification<Employee> hasGender(Gender gender) {
        return (root, query, cb) -> cb.equal(root.get("gender"), gender);
    }

    public static Specification<Employee> hiredBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get("hireDate"), from, to);
    }

    public static Specification<Employee> hasNationality(String nationality) {
        return (root, query, cb) -> cb.like(
            cb.lower(root.get("nationality")),
            "%" + nationality.toLowerCase() + "%"
        );
    }

    public static Specification<Employee> keywordSearch(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("firstName")),    pattern),
                cb.like(cb.lower(root.get("lastName")),     pattern),
                cb.like(cb.lower(root.get("employeeCode")), pattern),
                cb.like(cb.lower(root.get("workEmail")),    pattern)
            );
        };
    }

    public static Specification<Employee> probationExpiredBefore(LocalDate date) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("employmentStatus"), EmploymentStatus.PROBATION),
            cb.lessThanOrEqualTo(root.get("probationEndDate"), date),
            cb.isNotNull(root.get("probationEndDate"))
        );
    }
}
