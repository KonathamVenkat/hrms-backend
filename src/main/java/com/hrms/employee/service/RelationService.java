package com.hrms.employee.service;

import com.hrms.employee.dto.request.RelationRequest;
import com.hrms.employee.dto.response.RelationResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import java.util.List;

public interface RelationService {
    /** Active rows for dropdowns (read-only, for the roles that pick them). */
    List<MasterLookupResponse> getActiveLookup();
    List<RelationResponse> getAll();
    RelationResponse getById(Long id);
    RelationResponse create(RelationRequest request);
    RelationResponse update(Long id, RelationRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
