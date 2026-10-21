package com.hrms.employee.service;

import com.hrms.employee.dto.request.CityRequest;
import com.hrms.employee.dto.response.CityResponse;
import java.util.List;

public interface CityService {
    List<CityResponse> getAll();
    CityResponse getById(Long id);
    CityResponse create(CityRequest request);
    CityResponse update(Long id, CityRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
