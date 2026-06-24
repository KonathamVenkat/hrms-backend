package com.hrms.leave.service;

import com.hrms.leave.dto.request.AdjustBalanceRequest;
import com.hrms.leave.dto.request.InitializeBalancesRequest;
import com.hrms.leave.dto.response.InitializationResultResponse;
import com.hrms.leave.dto.response.LeaveBalanceResponse;

import java.util.List;

public interface LeaveBalanceService {

    /** Get all leave balances for an employee for a given year */
    List<LeaveBalanceResponse> getEmployeeBalances(Long employeeId, Integer year);

    /** Get all employees' balances for a given year — admin view */
    List<LeaveBalanceResponse> getAllBalancesForYear(Integer year);

    /**
     * Bulk initialize balances for all (or selected) employees.
     * Reads defaults from LEAVE_TYPES table.
     * Optionally carries forward unused annual leave.
     */
    InitializationResultResponse initializeBalances(InitializeBalancesRequest request);

    /** Initialize balances for a single new employee */
    List<LeaveBalanceResponse> initializeForEmployee(Long employeeId, Integer year);

    /** Manually adjust a single employee's balance */
    LeaveBalanceResponse adjustBalance(Long employeeId, AdjustBalanceRequest request);
}