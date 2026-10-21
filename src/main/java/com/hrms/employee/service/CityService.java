package com.hrms.employee.service;

import com.hrms.employee.dto.request.CityRequest;
import com.hrms.employee.dto.response.CityResponse;
import com.hrms.common.dto.PagedResponse;
import java.util.List;

public interface CityService {
    List<CityResponse> getAll();
    /** One page of cities, filtered by text (name, Arabic name or code), province and status; name order. */
    PagedResponse<CityResponse> search(String q, Long provinceId, Boolean active, int page, int size);
    CityResponse getById(Long id);
    CityResponse create(CityRequest request);
    CityResponse update(Long id, CityRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
