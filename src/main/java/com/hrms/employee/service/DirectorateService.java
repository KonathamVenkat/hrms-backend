package com.hrms.employee.service;

import com.hrms.employee.dto.request.DirectorateRequest;
import com.hrms.employee.dto.response.DirectorateResponse;
import java.util.List;

public interface DirectorateService {
    List<DirectorateResponse> getAll();
    DirectorateResponse getById(Long id);
    DirectorateResponse create(DirectorateRequest request);
    DirectorateResponse update(Long id, DirectorateRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
