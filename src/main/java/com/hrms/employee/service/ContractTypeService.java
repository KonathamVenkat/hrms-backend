package com.hrms.employee.service;

import com.hrms.employee.dto.request.ContractTypeRequest;
import com.hrms.employee.dto.response.ContractTypeResponse;
import java.util.List;

public interface ContractTypeService {
    List<ContractTypeResponse> getAll();
    ContractTypeResponse getById(Long id);
    ContractTypeResponse create(ContractTypeRequest request);
    ContractTypeResponse update(Long id, ContractTypeRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
