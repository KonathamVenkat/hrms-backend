package com.hrms.employee.dto.response;

import com.hrms.common.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data @Builder
public class EmployeeExperienceResponse {
    private Long id;
    private Long employeeId;
    private String employerName;
    private String employerNameAr;
    private String jobTitle;
    private String place;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private Long version;
}
