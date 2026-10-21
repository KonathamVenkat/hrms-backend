package com.hrms.employee.service;

import com.hrms.employee.dto.request.ProvinceRequest;
import com.hrms.employee.dto.response.ProvinceResponse;
import java.util.List;

public interface ProvinceService {
    List<ProvinceResponse> getAll();
    ProvinceResponse getById(Long id);
    ProvinceResponse create(ProvinceRequest request);
    ProvinceResponse update(Long id, ProvinceRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
