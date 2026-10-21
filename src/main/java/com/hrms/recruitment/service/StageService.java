package com.hrms.recruitment.service;

import com.hrms.recruitment.dto.request.StageRequest;
import com.hrms.recruitment.dto.response.RecruitmentLookupResponse;
import com.hrms.recruitment.dto.response.StageResponse;

import java.util.List;

public interface StageService {

    List<StageResponse> getAll();

    List<RecruitmentLookupResponse> getActiveLookup();

    StageResponse getById(Long id);

    StageResponse create(StageRequest request);

    StageResponse update(Long id, StageRequest request);

    void deactivate(Long id);

    void activate(Long id);

    void delete(Long id);
}
