package com.hrms.employee.service;

import com.hrms.employee.dto.request.OfficeLocationRequest;
import com.hrms.employee.dto.response.OfficeLocationResponse;
import java.util.List;

public interface OfficeLocationService {
    List<OfficeLocationResponse> getAllLocations();
    List<OfficeLocationResponse> getActiveLocations();
    OfficeLocationResponse       getLocationById(Long id);
    OfficeLocationResponse       createLocation(OfficeLocationRequest request);
    OfficeLocationResponse       updateLocation(Long id, OfficeLocationRequest request);
    void                         deactivateLocation(Long id);
    void                         activateLocation(Long id);
}
