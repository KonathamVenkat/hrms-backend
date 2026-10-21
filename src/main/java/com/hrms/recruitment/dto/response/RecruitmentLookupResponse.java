package com.hrms.recruitment.dto.response;

import lombok.Builder;

/** One row of a read-only dropdown (active rows only); {@code kind} carries the stage type or reason scope. */
@Builder
public record RecruitmentLookupResponse(Long id, String code, String name, String nameAr, String kind) { }
