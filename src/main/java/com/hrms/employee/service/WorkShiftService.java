package com.hrms.employee.service;

import com.hrms.employee.dto.request.WorkShiftRequest;
import com.hrms.employee.dto.response.WorkShiftResponse;
import java.util.List;

public interface WorkShiftService {

    List<WorkShiftResponse> getAllShifts();
    List<WorkShiftResponse> getActiveShifts();
    WorkShiftResponse       getShiftById(Long id);
    WorkShiftResponse       createShift(WorkShiftRequest request);
    WorkShiftResponse       updateShift(Long id, WorkShiftRequest request);
    void                    deactivateShift(Long id);
    void                    activateShift(Long id);
}