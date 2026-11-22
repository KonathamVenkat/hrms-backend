package com.hrms.payroll.service;

import com.hrms.payroll.dto.request.EmployeeSalaryRequest;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse;
import com.hrms.payroll.dto.response.PayScaleBasisResponse;
import com.hrms.common.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeSalaryService {
    EmployeeSalaryResponse assign(EmployeeSalaryRequest request);
    EmployeeSalaryResponse getCurrentSalary(Long employeeId);
    List<EmployeeSalaryResponse> getSalaryHistory(Long employeeId);
    PagedResponse<EmployeeSalaryResponse> getAllCurrent(Long structureId, Pageable pageable);
    /** The structure and basic the pay scale gives the employee's current grade and increment on a date. */
    PayScaleBasisResponse getPayScaleBasis(Long employeeId, LocalDate onDate);
}
