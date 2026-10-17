package com.hrms.employee.service;

import com.hrms.employee.dto.request.DepartmentRequest;
import com.hrms.employee.dto.response.DepartmentLookupResponse;
import com.hrms.employee.dto.response.DepartmentResponse;
import java.util.List;

public interface DepartmentService {
    List<DepartmentResponse> getAllDepartments();
    /** Active departments for dropdowns. */
    List<DepartmentLookupResponse> getActiveLookup();
    DepartmentResponse       getDepartmentById(Long id);
    DepartmentResponse       createDepartment(DepartmentRequest request);
    DepartmentResponse       updateDepartment(Long id, DepartmentRequest request);
    void                     deactivateDepartment(Long id);
    void                     activateDepartment(Long id);
    /** Hard delete; only allowed while no designation or employee job record uses the department. */
    void                     deleteDepartment(Long id);
}
