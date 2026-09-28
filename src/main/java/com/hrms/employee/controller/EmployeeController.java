package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.dto.request.CreateEmployeeRequest;
import com.hrms.employee.dto.request.EmployeeFilterRequest;
import com.hrms.employee.dto.request.UpdateEmployeeRequest;
import com.hrms.employee.dto.response.EmployeeDetailResponse;
import com.hrms.employee.dto.response.EmployeeResponse;
import com.hrms.employee.dto.response.EmployeeSummaryResponse;
import com.hrms.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for all HRMS employee operations.
 *
 * <h3>Base path:</h3> {@code /api/v1/employees}
 *
 * <h3>Security:</h3>
 * All endpoints require a valid JWT Bearer token (enforced by Spring Security filter).
 * Role-based access is enforced via {@code @PreAuthorize} annotations:
 * <ul>
 *   <li>{@code HR_ADMIN} — full CRUD access</li>
 *   <li>{@code HR_MANAGER} — read + update access</li>
 *   <li>{@code EMPLOYEE} — read own record only (enforced at service layer)</li>
 * </ul>
 *
 * <h3>Response envelope:</h3>
 * All responses are wrapped in {@link ApiResponse}{@code <T>} for consistent
 * Angular-side handling. The Angular HTTP interceptor can check {@code success}
 * and {@code statusCode} before accessing {@code data}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/employees")
//@RequiredArgsConstructor
@Validated
@Tag(name = "Employee Management", description = "APIs for managing HRMS employee records")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
    	this.employeeService = employeeService;
    }
    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/employees  →  Create new employee
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    @Operation(
        summary     = "Create a new employee",
        description = "Registers a new employee in the HRMS system. "
                    + "Employee code, work email, and personal email must be unique."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Employee created successfully",
            content = @Content(schema = @Schema(implementation = EmployeeResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate employee code or email"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Business rule violation")
    })
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {

    //    log.info("POST /api/v1/employees - Creating employee: {}", request.getEmployeeCode());
        EmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.created("Employee created successfully", response));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/employees/{id}  →  Get employee by ID
    // ──────────────────────────────────────────────────────────────────────────

    /*  @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER', 'EMPLOYEE')")
    @Operation(
        summary     = "Get employee by ID",
        description = "Returns full employee details by their database primary key."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Employee found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployeeById(
            @Parameter(description = "Employee database ID", example = "1001")
            @PathVariable @Positive(message = "Employee ID must be a positive number") Long id) {

        log.debug("GET /api/v1/employees/{}", id);
        return ResponseEntity.ok(ApiResponse.success(employeeService.getEmployeeById(id)));
    }*/
    
    /**
     * GET /api/v1/employees/{id}
     * Returns full employee detail with department and designation.
     * Accessible by HR_ADMIN, HR_MANAGER, and the employee themselves.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse<EmployeeDetailResponse>> getEmployeeById(
            @PathVariable Long id) {
 
        log.info("GET /api/v1/employees/{}", id);
 
        EmployeeDetailResponse detail = employeeService.getEmployeeById(id);
 
        return ResponseEntity.ok(
            ApiResponse.<EmployeeDetailResponse>builder()
                .success(true)
                .message("Employee fetched successfully")
                .data(detail)
                .statusCode(200)
                .build()
        );
    }
 

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/employees/code/{employeeCode}  →  Get by employee code
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/code/{employeeCode}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER', 'EMPLOYEE')")
    @Operation(
        summary     = "Get employee by employee code",
        description = "Returns full employee details by the unique employee code (e.g., EMP-2024-001)."
    )
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployeeByCode(
            @Parameter(description = "Unique employee code", example = "EMP-2024-001")
            @PathVariable @NotBlank String employeeCode) {

        return ResponseEntity.ok(
            ApiResponse.success(employeeService.getEmployeeByCode(employeeCode))
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/employees/search  →  Filtered + paginated list
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    @Operation(
        summary     = "Search and filter employees",
        description = "Returns a paginated, filterable, sortable list of employee summaries. "
                    + "Designed for the Angular Material table with server-side data source. "
                    + "All filter fields are optional — omit to retrieve all active employees."
    )
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeSummaryResponse>>> searchEmployees(
            @Valid @RequestBody EmployeeFilterRequest filterRequest) {

        log.debug("POST /api/v1/employees/search - Filter: {}", filterRequest);
        PagedResponse<EmployeeSummaryResponse> result = employeeService.getEmployees(filterRequest);
        return ResponseEntity.ok(
        		ApiResponse.<PagedResponse<EmployeeSummaryResponse>>builder()
                .success(true)
                .message("Employees fetched successfully")
                .data(employeeService.getEmployees(filterRequest))
                .statusCode(200)
                .build()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/employees/lookup  →  All active employees (for dropdowns)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER', 'EMPLOYEE')")
    @Operation(
        summary     = "Get all active employees for lookup/autocomplete",
        description = "Returns a lightweight list of all active employees. "
                    + "Intended for Angular autocomplete fields and dropdown selectors "
                    + "(e.g., manager selection, leave approver, payroll assignment)."
    )
    public ResponseEntity<ApiResponse<List<EmployeeSummaryResponse>>> getAllEmployeesForLookup() {
        return ResponseEntity.ok(
            ApiResponse.success(employeeService.getAllActiveEmployeesForLookup())
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/employees/{id}  →  Full update
    // ──────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    @Operation(
        summary     = "Update employee details",
        description = "Fully updates an employee's mutable fields. "
                    + "Fields such as employeeCode and workEmail are immutable via this endpoint. "
                    + "Audit fields are managed automatically."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Employee updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Employee not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate email"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Business rule violation")
    })
    public ResponseEntity<ApiResponse<EmployeeDetailResponse>> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
 
        log.info("PUT /api/v1/employees/{}", id);
 
        EmployeeDetailResponse updated = employeeService.updateEmployee(id, request);
 
        return ResponseEntity.ok(
            ApiResponse.<EmployeeDetailResponse>builder()
                .success(true)
                .message("Employee updated successfully")
                .data(updated)
                .statusCode(200)
                .build()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/employees/{id}/photo  →  Update profile photo only
    // ──────────────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER', 'EMPLOYEE')")
    @Operation(
        summary     = "Update employee profile photo",
        description = "Updates only the employee's profile photo URL after a successful "
                    + "file upload to the object storage (S3/Azure Blob). "
                    + "The file upload itself is handled by a separate media upload service."
    )
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateProfilePhoto(
            @PathVariable @Positive Long id,
            @RequestBody Map<String, String> body) {

        String photoUrl = body.get("profilePhotoUrl");
        if (photoUrl == null || photoUrl.isBlank()) {
            return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, "profilePhotoUrl is required in request body"));
        }
        return ResponseEntity.ok(
            ApiResponse.success("Profile photo updated", employeeService.updateProfilePhoto(id, photoUrl))
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/employees/{id}  →  Soft delete (deactivate)
    // ──────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @Operation(
        summary     = "Deactivate an employee (soft delete)",
        description = "Sets IS_ACTIVE = 0 on the employee record. "
                    + "Physical deletion is never performed — records are retained for "
                    + "payroll history, leave audit, and compliance purposes. "
                    + "Only HR_ADMIN role can perform this operation."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Employee deactivated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Employee not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<ApiResponse<Void>> deactivateEmployee(
            @Parameter(description = "Employee database ID")
            @PathVariable @Positive Long id) {

        log.info("DELETE /api/v1/employees/{} (soft delete)", id);
        employeeService.deactivateEmployee(id);
        return ResponseEntity.ok(ApiResponse.noContent("Employee deactivated successfully"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/employees/{id}/reactivate  →  Reactivate deactivated employee
    // ──────────────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @Operation(
        summary     = "Reactivate a deactivated employee",
        description = "Re-enables access for a previously deactivated employee. "
                    + "Used for rehires or corrections. Only HR_ADMIN role can perform this."
    )
    public ResponseEntity<ApiResponse<EmployeeResponse>> reactivateEmployee(
            @PathVariable @Positive Long id) {

        log.info("PATCH /api/v1/employees/{}/reactivate", id);
        EmployeeResponse response = employeeService.reactivateEmployee(id);
        return ResponseEntity.ok(ApiResponse.success("Employee reactivated successfully", response));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/employees/{id}/exists  →  Existence check (internal use)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/exists")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER', 'SYSTEM')")
    @Operation(
        summary     = "Check if an active employee exists",
        description = "Lightweight existence check used by other microservices "
                    + "(leave-service, payroll-service) via internal Feign clients "
                    + "to verify employee IDs before creating linked records."
    )
    public ResponseEntity<ApiResponse<Boolean>> checkEmployeeExists(
            @PathVariable @Positive Long id) {
        return ResponseEntity.ok(
            ApiResponse.success(employeeService.existsActiveEmployee(id))
        );
    }
    
    
 // ADD this to your existing EmployeeController

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeSummaryResponse>>> getEmployees(
            @RequestParam(required = false)              String  keyword,
            @RequestParam(required = false)              Long    departmentId,
            @RequestParam(required = false)              String  employmentStatus,
            @RequestParam(required = false)              String  employmentType,
            @RequestParam(required = false)              String  gender,
            @RequestParam(required = false)              Boolean isActive,
            @RequestParam(defaultValue = "0")            int     page,
            @RequestParam(defaultValue = "10")           int     size,
            @RequestParam(defaultValue = "employeeCode") String  sortBy,
            @RequestParam(defaultValue = "ASC")          String  sortDir
    ) {
        EmployeeFilterRequest req = new EmployeeFilterRequest();
        req.setKeyword(keyword);
        req.setDepartmentId(departmentId);

        // ✅ Safe String → Enum conversion
        if (employmentStatus != null && !employmentStatus.isBlank()) {
            try { req.setEmploymentStatus(EmploymentStatus.valueOf(employmentStatus.toUpperCase())); }
            catch (IllegalArgumentException ignored) { /* invalid value — skip filter */ }
        }
        if (employmentType != null && !employmentType.isBlank()) {
            try { req.setEmploymentType(EmploymentType.valueOf(employmentType.toUpperCase())); }
            catch (IllegalArgumentException ignored) { }
        }
        if (gender != null && !gender.isBlank()) {
            try { req.setGender(Gender.valueOf(gender.toUpperCase())); }
            catch (IllegalArgumentException ignored) { }
        }

        if (isActive != null) {
            req.setIsActive(isActive);
        }
        req.setPage(page);
        req.setSize(size);
        req.setSortBy(sortBy);
        req.setSortDir(sortDir);

        return ResponseEntity.ok(
                ApiResponse.<PagedResponse<EmployeeSummaryResponse>>builder()
                    .success(true)
                    .message("Employees fetched successfully")
                    .data(employeeService.getEmployees(req))
                    .statusCode(200)
                    .build());
    }
    
    
}
