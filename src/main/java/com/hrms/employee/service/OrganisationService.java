package com.hrms.employee.service;

import com.hrms.employee.dto.request.BranchPlacementRequest;
import com.hrms.employee.dto.request.BranchAssignmentRequest;
import com.hrms.employee.dto.response.BranchAssignmentResult;
import com.hrms.employee.dto.response.BranchLookupResponse;
import com.hrms.employee.dto.response.EmployeeBranchGapResponse;
import com.hrms.employee.dto.response.OrganisationSummaryResponse;
import com.hrms.employee.dto.response.BranchPlacementResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Organisation structure: which office location each branch sits in, who manages it, and who belongs to it. */
public interface OrganisationService {

    /** Every branch with its location and manager (one query each for locations and managers, never per row). */
    List<BranchPlacementResponse> listBranches();

    /** Active branches for a dropdown, optionally only those of one location. */
    List<BranchLookupResponse> lookupBranches(Long locationId);

    /**
     * Sets a branch's location and manager (null manager clears it). The manager must be an active employee;
     * one person may manage several branches. A stale version is refused with 409.
     */
    BranchPlacementResponse setPlacement(Long branchId, BranchPlacementRequest request);

    /** What is still missing: employees without a branch, active branches without a manager or a location. */
    OrganisationSummaryResponse summary();

    /** Active employees whose current job has no branch, searchable by name or code; page size is capped at 100. */
    Page<EmployeeBranchGapResponse> employeesWithoutBranch(String q, Pageable pageable);

    /**
     * Active employees who could manage a branch: those working at the location (null = anywhere), searchable by name
     * or code; page size is capped at 100. Same row shape as the back-fill list.
     */
    Page<EmployeeBranchGapResponse> managerCandidates(Long locationId, String q, Pageable pageable);

    /**
     * Back-fill: gives employees their first branch. Each row is checked on its own: valid rows are applied and every
     * other row is listed with a reason. A branch is only set while the current job has none (a real move goes
     * through a new job assignment, which keeps history).
     */
    BranchAssignmentResult assignBranches(BranchAssignmentRequest request);

    /** Clears the employee as manager of every branch; called when the employee is deactivated. Idempotent. */
    void clearManagerOf(Long employeeId);
}
