package com.hrms.payroll.service;

import com.hrms.payroll.dto.request.SalaryComponentRequest;
import com.hrms.payroll.dto.response.SalaryComponentResponse;
import com.hrms.payroll.enums.ComponentType;

import java.util.List;

public interface SalaryComponentService {
    List<SalaryComponentResponse> getAll(Boolean activeOnly);
    List<SalaryComponentResponse> getByType(ComponentType type);
    SalaryComponentResponse getById(Long id);
    SalaryComponentResponse create(SalaryComponentRequest request);
    SalaryComponentResponse update(Long id, SalaryComponentRequest request);
    void toggleActive(Long id, boolean active);
}
