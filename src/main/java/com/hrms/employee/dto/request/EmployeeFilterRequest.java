package com.hrms.employee.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Multi-criteria filter request for the employee list endpoint.
 *
 * <p>Passed as a {@code POST} body to {@code /api/v1/employees/search} to support
 * the Angular Material table's server-side filtering, sorting, and pagination.
 * Angular sends this as a JSON body rather than query params to support complex
 * date-range and multi-value filters cleanly.</p>
 *
 * <p>All fields are optional — only non-null fields are applied as filter criteria
 * via {@link com.hrms.employee.repository.specification.EmployeeSpecification}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeFilterRequest {

    // ──────────────────────────────────────────────────────────────────────────
    // Text search (Angular search bar — fuzzy match across name, code, email)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Free-text keyword search across firstName, lastName, employeeCode, workEmail.
     * Passed to {@link com.hrms.employee.repository.EmployeeRepository#searchByKeyword}.
     */
    @JsonProperty("keyword")
    private String keyword;

    // ──────────────────────────────────────────────────────────────────────────
    // Enum filters (Angular MatSelect dropdowns)
    // ──────────────────────────────────────────────────────────────────────────

    @JsonProperty("gender")
    private Gender gender;

    @JsonProperty("employmentStatus")
    private EmploymentStatus employmentStatus;

    @JsonProperty("employmentType")
    private EmploymentType employmentType;

    // ──────────────────────────────────────────────────────────────────────────
    // Demographic filters
    // ──────────────────────────────────────────────────────────────────────────

    @JsonProperty("nationality")
    private String nationality;

    // ──────────────────────────────────────────────────────────────────────────
    // Date range filters (Angular MatDateRangePicker)
    // ──────────────────────────────────────────────────────────────────────────

    /** Hire date range — start (inclusive). ISO-8601: "2024-01-01" */
    @JsonProperty("hireDateFrom")
    private LocalDate hireDateFrom;

    /** Hire date range — end (inclusive). ISO-8601: "2024-12-31" */
    @JsonProperty("hireDateTo")
    private LocalDate hireDateTo;

    /** Date of birth range — start (for age-band filtering). */
    @JsonProperty("dateOfBirthFrom")
    private LocalDate dateOfBirthFrom;

    /** Date of birth range — end. */
    @JsonProperty("dateOfBirthTo")
    private LocalDate dateOfBirthTo;
    
    @JsonProperty("departmentId")
    private Long departmentId; 

    // ──────────────────────────────────────────────────────────────────────────
    // Soft delete filter (default: active only)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Filter by active status.
     * {@code true}  → active employees only (default for most views).
     * {@code false} → inactive / deactivated employees.
     * {@code null}  → all employees regardless of active status (admin view).
     */
    @JsonProperty("isActive")
    @Builder.Default
    private Boolean isActive = true;

    // ──────────────────────────────────────────────────────────────────────────
    // Pagination & sorting (mirrors Angular MatPaginator + MatSort)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Zero-based page number. Matches Angular MatPaginator {@code pageIndex}.
     */
    @JsonProperty("page")
    @Builder.Default
    private int page = 0;

    /**
     * Number of rows per page. Matches Angular MatPaginator {@code pageSize}.
     * Typical values: 10, 25, 50, 100.
     */
    @JsonProperty("size")
    @Builder.Default
    private int size = 25;

    /**
     * Column name to sort by (must match {@link com.hrms.employee.entity.Employee} field name).
     * Matches Angular MatSort {@code active}.
     * Example: "lastName", "hireDate", "employeeCode"
     */
    @JsonProperty("sortBy")
    @Builder.Default
    private String sortBy = "id";

    /**
     * Sort direction. Matches Angular MatSort {@code direction}.
     * Accepted values: "asc", "desc"
     */
    @JsonProperty("sortDir")
    @Builder.Default
    private String sortDir = "asc";
}
