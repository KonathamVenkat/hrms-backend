package com.hrms.payroll.service;

import com.hrms.payroll.dto.request.SalaryStructureRequest;
import com.hrms.payroll.dto.response.SalaryStructureResponse;

import java.util.List;

public interface SalaryStructureService {
    List<SalaryStructureResponse> getAll(Boolean activeOnly);
    SalaryStructureResponse getById(Long id);
    SalaryStructureResponse create(SalaryStructureRequest request);
    SalaryStructureResponse update(Long id, SalaryStructureRequest request);
    void toggleActive(Long id, boolean active);
}
