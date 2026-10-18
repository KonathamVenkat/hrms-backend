package com.hrms.employee.service;

import com.hrms.employee.dto.request.CompanyBranchRequest;
import com.hrms.employee.dto.response.CompanyBranchResponse;
import java.util.List;

public interface CompanyBranchService {
    List<CompanyBranchResponse> getAll();
    CompanyBranchResponse getById(Long id);
    CompanyBranchResponse create(CompanyBranchRequest request);
    CompanyBranchResponse update(Long id, CompanyBranchRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
