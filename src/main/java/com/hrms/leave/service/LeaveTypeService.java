package com.hrms.leave.service;
import com.hrms.leave.dto.request.LeaveTypeRequest;
import com.hrms.leave.dto.response.LeaveTypeResponse;
import java.util.List;

public interface LeaveTypeService {
    List<LeaveTypeResponse> getAllLeaveTypes();
    List<LeaveTypeResponse> getActiveLeaveTypes();
    LeaveTypeResponse       getLeaveTypeById(Long id);
    LeaveTypeResponse       createLeaveType(LeaveTypeRequest request);
    LeaveTypeResponse       updateLeaveType(Long id, LeaveTypeRequest request);
    void                    deactivateLeaveType(Long id);
    void                    activateLeaveType(Long id);
}
 