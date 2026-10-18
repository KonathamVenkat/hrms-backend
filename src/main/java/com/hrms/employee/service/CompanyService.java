package com.hrms.employee.service;

import com.hrms.employee.dto.request.CompanyRequest;
import com.hrms.employee.dto.response.CompanyResponse;
import java.util.List;

public interface CompanyService {
    List<CompanyResponse> getAll();
    CompanyResponse getById(Long id);
    CompanyResponse create(CompanyRequest request);
    CompanyResponse update(Long id, CompanyRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
