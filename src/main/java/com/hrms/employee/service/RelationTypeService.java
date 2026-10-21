package com.hrms.employee.service;

import com.hrms.employee.dto.request.RelationTypeRequest;
import com.hrms.employee.dto.response.RelationTypeResponse;
import java.util.List;

public interface RelationTypeService {
    List<RelationTypeResponse> getAll();
    RelationTypeResponse getById(Long id);
    RelationTypeResponse create(RelationTypeRequest request);
    RelationTypeResponse update(Long id, RelationTypeRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
