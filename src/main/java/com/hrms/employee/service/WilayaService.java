package com.hrms.employee.service;

import com.hrms.employee.dto.request.WilayaRequest;
import com.hrms.employee.dto.response.WilayaResponse;
import java.util.List;

public interface WilayaService {
    List<WilayaResponse> getAll();
    WilayaResponse getById(Long id);
    WilayaResponse create(WilayaRequest request);
    WilayaResponse update(Long id, WilayaRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
