package com.hrms.leave.service;

import com.hrms.common.dto.PagedResponse;
import com.hrms.leave.dto.request.*;
import com.hrms.leave.dto.response.*;
import java.util.List;

public interface LeaveService {

    // Employee actions
    LeaveRequestResponse                applyLeave(Long employeeId, CreateLeaveRequest request);
    PagedResponse<LeaveRequestResponse> getLeavesByEmployee(Long employeeId, LeaveFilterRequest filter);
    LeaveRequestResponse                getLeaveById(Long leaveReqId);
    void                                cancelLeave(Long leaveReqId, Long employeeId);
    List<LeaveBalanceResponse>          getBalances(Long employeeId, Integer year);

    // HR/Manager actions
    PagedResponse<LeaveRequestResponse> getAllLeaves(LeaveFilterRequest filter);
    LeaveRequestResponse                processLeave(Long leaveReqId, ApproveLeaveRequest request, String approver);
    Long                                getPendingCount();
}
