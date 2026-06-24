package com.hrms.payroll.service;

import com.hrms.payroll.dto.request.EmployeeSalaryRequest;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse;
import com.hrms.common.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EmployeeSalaryService {
    EmployeeSalaryResponse assign(EmployeeSalaryRequest request);
    EmployeeSalaryResponse getCurrentSalary(Long employeeId);
    List<EmployeeSalaryResponse> getSalaryHistory(Long employeeId);
    PagedResponse<EmployeeSalaryResponse> getAllCurrent(Long structureId, Pageable pageable);
}
