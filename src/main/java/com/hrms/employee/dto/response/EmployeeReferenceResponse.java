package com.hrms.employee.dto.response;

import com.hrms.common.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data @Builder
public class EmployeeReferenceResponse {
    private Long id;
    private Long employeeId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String firstNameAr;
    private String middleNameAr;
    private String lastNameAr;
    private String jobTitle;
    private String address;
    private String phone;
    private String email;
    private Boolean isActive;
    private Long version;
}
