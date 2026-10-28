package com.hrms.employee.service;

import com.hrms.employee.dto.request.NationalityRequest;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.dto.response.NationalityResponse;
import java.util.List;

public interface NationalityService {
    List<MasterLookupResponse> getActiveLookup();
    /** Active nationalities that have a country name, listed by that name (the address form's Country). */
    List<MasterLookupResponse> getActiveCountryLookup();
    List<NationalityResponse> getAll();
    NationalityResponse getById(Long id);
    NationalityResponse create(NationalityRequest request);
    NationalityResponse update(Long id, NationalityRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
