package com.hrms.employee.service;

import com.hrms.employee.dto.request.DesignationRequest;
import com.hrms.employee.dto.response.DesignationResponse;
import java.util.List;

public interface DesignationService {
    List<DesignationResponse> getAllDesignations();
    DesignationResponse       getDesignationById(Long id);
    DesignationResponse       createDesignation(DesignationRequest request);
    DesignationResponse       updateDesignation(Long id, DesignationRequest request);
    void                      deactivateDesignation(Long id);
    void                      activateDesignation(Long id);
    /** Hard delete; only allowed while no employee job record uses the designation. */
    void                      deleteDesignation(Long id);
}
