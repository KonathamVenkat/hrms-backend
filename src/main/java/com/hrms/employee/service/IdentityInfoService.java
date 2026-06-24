package com.hrms.employee.service;

import com.hrms.employee.dto.request.IdentityInfoRequest;
import com.hrms.employee.dto.response.IdentityInfoResponse;

public interface IdentityInfoService {

    /** Get identity info — returns empty shell if not yet created */
    IdentityInfoResponse getIdentityInfo(Long employeeId);

    /** Create or update identity info (upsert) */
    IdentityInfoResponse saveIdentityInfo(Long employeeId, IdentityInfoRequest request);
}
