package com.hrms.employee.dto.response;

import com.hrms.common.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data @Builder
public class EmployeeLanguageResponse {
    private Long id;
    private Long employeeId;
    private Long languageId;
    private String languageName;
    private String languageNameAr;
    private Long readLevelId;
    private String readLevelName;
    private String readLevelNameAr;
    private Long writeLevelId;
    private String writeLevelName;
    private String writeLevelNameAr;
    private Long speakLevelId;
    private String speakLevelName;
    private String speakLevelNameAr;
    private Boolean isActive;
    private Long version;
}
