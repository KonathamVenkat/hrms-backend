package com.hrms.employee.dto.response;

import com.hrms.common.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data @Builder
public class EmployeeTrainingResponse {
    private Long id;
    private Long employeeId;
    private String courseName;
    private String courseNameAr;
    private String provider;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private Long version;
}
