package com.hrms.employee.service;

import com.hrms.employee.dto.request.ReligionRequest;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.dto.response.ReligionResponse;
import java.util.List;

public interface ReligionService {
    List<MasterLookupResponse> getActiveLookup();
    List<ReligionResponse> getAll();
    ReligionResponse getById(Long id);
    ReligionResponse create(ReligionRequest request);
    ReligionResponse update(Long id, ReligionRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
