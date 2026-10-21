package com.hrms.employee.service;

import com.hrms.employee.dto.request.RegionRequest;
import com.hrms.employee.dto.response.RegionResponse;
import java.util.List;

public interface RegionService {
    List<RegionResponse> getAll();
    RegionResponse getById(Long id);
    RegionResponse create(RegionRequest request);
    RegionResponse update(Long id, RegionRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
