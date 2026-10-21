package com.hrms.recruitment.dto.response;

import com.hrms.recruitment.enums.ReasonScope;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record RejectionReasonResponse(
    Long id,
    String code,
    String name,
    String nameAr,
    ReasonScope appliesTo,
    Integer sortOrder,
    boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version
) { }
