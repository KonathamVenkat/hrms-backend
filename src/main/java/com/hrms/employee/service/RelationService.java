package com.hrms.employee.service;

import com.hrms.employee.dto.request.RelationRequest;
import com.hrms.employee.dto.response.RelationResponse;
import java.util.List;

public interface RelationService {
    List<RelationResponse> getAll();
    RelationResponse getById(Long id);
    RelationResponse create(RelationRequest request);
    RelationResponse update(Long id, RelationRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
