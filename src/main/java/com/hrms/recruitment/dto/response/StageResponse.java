package com.hrms.recruitment.dto.response;

import com.hrms.recruitment.enums.StageType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record StageResponse(
    Long id,
    String code,
    String name,
    String nameAr,
    StageType stageType,
    Integer sortOrder,
    boolean isSystem,
    boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version
) { }
