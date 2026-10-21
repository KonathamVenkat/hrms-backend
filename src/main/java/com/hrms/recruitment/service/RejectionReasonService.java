package com.hrms.recruitment.service;

import com.hrms.recruitment.dto.request.RejectionReasonRequest;
import com.hrms.recruitment.dto.response.RecruitmentLookupResponse;
import com.hrms.recruitment.dto.response.RejectionReasonResponse;
import com.hrms.recruitment.enums.ReasonScope;

import java.util.List;

public interface RejectionReasonService {

    List<RejectionReasonResponse> getAll();

    List<RecruitmentLookupResponse> getActiveLookup(ReasonScope appliesTo);

    RejectionReasonResponse getById(Long id);

    RejectionReasonResponse create(RejectionReasonRequest request);

    RejectionReasonResponse update(Long id, RejectionReasonRequest request);

    void deactivate(Long id);

    void activate(Long id);

    void delete(Long id);
}
