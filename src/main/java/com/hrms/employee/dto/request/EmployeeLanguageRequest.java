package com.hrms.employee.dto.request;

import com.hrms.common.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeLanguageRequest {

    @NotNull(message = "Language is required")
    private Long languageId;

    private Long readLevelId;

    private Long writeLevelId;

    private Long speakLevelId;

    /** The version the screen was loaded with. Required to update; ignored when adding. */
    private Long version;
}
