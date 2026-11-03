package com.hrms.employee.service;

import com.hrms.employee.dto.request.SpecializationRequest;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.dto.response.SpecializationResponse;
import java.util.List;

public interface SpecializationService {
    List<SpecializationResponse> getAll();
    /** Active specializations for dropdowns. */
    List<MasterLookupResponse> getActiveLookup();
    SpecializationResponse getById(Long id);
    SpecializationResponse create(SpecializationRequest request);
    SpecializationResponse update(Long id, SpecializationRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
