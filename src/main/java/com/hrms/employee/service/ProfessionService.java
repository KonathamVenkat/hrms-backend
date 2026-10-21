package com.hrms.employee.service;

import com.hrms.employee.dto.request.ProfessionRequest;
import com.hrms.employee.dto.response.ProfessionResponse;
import java.util.List;

public interface ProfessionService {
    List<ProfessionResponse> getAll();
    ProfessionResponse getById(Long id);
    ProfessionResponse create(ProfessionRequest request);
    ProfessionResponse update(Long id, ProfessionRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
