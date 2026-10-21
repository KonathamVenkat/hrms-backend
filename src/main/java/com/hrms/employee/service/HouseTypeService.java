package com.hrms.employee.service;

import com.hrms.employee.dto.request.HouseTypeRequest;
import com.hrms.employee.dto.response.HouseTypeResponse;
import java.util.List;

public interface HouseTypeService {
    List<HouseTypeResponse> getAll();
    HouseTypeResponse getById(Long id);
    HouseTypeResponse create(HouseTypeRequest request);
    HouseTypeResponse update(Long id, HouseTypeRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
