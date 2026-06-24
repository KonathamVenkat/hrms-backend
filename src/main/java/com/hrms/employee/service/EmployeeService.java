package com.hrms.employee.service;

import com.hrms.common.dto.PagedResponse;
import com.hrms.employee.dto.request.CreateEmployeeRequest;
import com.hrms.employee.dto.request.EmployeeFilterRequest;
import com.hrms.employee.dto.request.UpdateEmployeeRequest;
import com.hrms.employee.dto.response.EmployeeDetailResponse;
import com.hrms.employee.dto.response.EmployeeResponse;
import com.hrms.employee.dto.response.EmployeeSummaryResponse;

import java.util.List;

/**
 * Service contract for all employee business operations.
 *
 * <p>This interface is the boundary between the controller layer (HTTP concerns)
 * and the implementation layer (business logic, persistence). Keeping the interface
 * separate allows the microservice to swap implementations or add decorators
 * (e.g., caching, event publishing) without changing the controller.</p>
 */
public interface EmployeeService {

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new employee record.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>Employee code must be globally unique</li>
     *   <li>Work email must be globally unique</li>
     *   <li>Personal email must be globally unique</li>
     *   <li>Date of birth must be in the past</li>
     *   <li>Hire date must not be before date of birth</li>
     *   <li>Probation end date, if provided, must be after hire date</li>
     *   <li>Confirmation date, if provided, must be after or equal to probation end date</li>
     * </ul>
     * </p>
     *
     * @param request  validated create request DTO
     * @return         the newly created employee's full detail response
     */
    EmployeeResponse createEmployee(CreateEmployeeRequest request);

    // ──────────────────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves the full details of an active employee by their database ID.
     *
     * @param id  the employee's database primary key
     * @return    full employee detail response
     * @throws com.hrms.common.exception.ResourceNotFoundException if not found or inactive
     */
   // EmployeeResponse getEmployeeById(Long id);

    /**
     * Retrieves the full details of an employee by employee code.
     *
     * @param employeeCode  the unique employee code (e.g., "EMP-2024-001")
     * @return              full employee detail response
     */
    EmployeeResponse getEmployeeByCode(String employeeCode);

    /**
     * Returns a paginated, filtered, and sorted list of employee summaries.
     *
     * <p>Supports the Angular Material table with server-side pagination, sorting,
     * and multi-criteria filtering via {@link EmployeeFilterRequest}.</p>
     *
     * @param filterRequest  filter + pagination + sort parameters
     * @return               paginated list of employee summary DTOs
     */
    PagedResponse<EmployeeSummaryResponse> getEmployees(EmployeeFilterRequest filterRequest);

    /**
     * Returns a lightweight list of all active employees for autocomplete/lookup fields.
     * No pagination — intended for dropdowns where the full list is needed client-side.
     *
     * @return  list of all active employee summaries
     */
    List<EmployeeSummaryResponse> getAllActiveEmployeesForLookup();

    // ──────────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fully replaces the updatable fields of an employee record (HTTP PUT semantics).
     *
     * <p>The following fields are NOT updated via this method:
     * <ul>
     *   <li>{@code employeeCode} — requires a dedicated approval workflow</li>
     *   <li>{@code workEmail} — requires IT provisioning</li>
     *   <li>Audit fields — managed by the framework</li>
     * </ul>
     * </p>
     *
     * @param id      the employee's primary key
     * @param request validated update request DTO
     * @return        updated employee full detail response
     */
    	EmployeeDetailResponse updateEmployee(Long id, UpdateEmployeeRequest request);
    

    /**
     * Updates only the employee's profile photo URL (after a successful file upload).
     *
     * @param id      the employee's primary key
     * @param photoUrl  the new profile photo URL
     * @return        updated employee summary
     */
    EmployeeResponse updateProfilePhoto(Long id, String photoUrl);

    // ──────────────────────────────────────────────────────────────────────────
    // Delete / Deactivation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Soft-deletes an employee by setting {@code IS_ACTIVE = 0}.
     *
     * <p>Physical deletion is never performed — employee records must be retained
     * for payroll history, leave records, and audit compliance.</p>
     *
     * @param id  the employee's primary key
     */
    void deactivateEmployee(Long id);

    /**
     * Reactivates a previously deactivated employee.
     *
     * @param id  the employee's primary key
     * @return    the reactivated employee's full detail response
     */
    EmployeeResponse reactivateEmployee(Long id);

    // ──────────────────────────────────────────────────────────────────────────
    // Existence / validation utilities (used by other microservices via Feign)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Checks whether an employee with the given ID exists and is active.
     * Called by leave-service, payroll-service, etc., via the internal API.
     *
     * @param id  the employee's primary key
     * @return    {@code true} if an active employee exists with this ID
     */
    boolean existsActiveEmployee(Long id);

    /**
     * Validates that a list of employee IDs all correspond to active employees.
     * Used in bulk operations (e.g., bulk status updates).
     *
     * @param ids  list of employee IDs to validate
     * @return     {@code true} if ALL given IDs refer to active employees
     */
    boolean allEmployeesExist(List<Long> ids);
    
    EmployeeDetailResponse getEmployeeById(Long id);
}
