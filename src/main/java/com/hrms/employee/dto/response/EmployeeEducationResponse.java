package com.hrms.employee.dto.response;

import com.hrms.common.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data @Builder
public class EmployeeEducationResponse {
    private Long id;
    private Long employeeId;
    private Long educationLevelId;
    private String educationLevelName;
    private String educationLevelNameAr;
    private String qualification;
    private String qualificationAr;
    private String institution;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private Long version;
}
