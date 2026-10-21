package com.hrms.employee.dto.response;

import com.hrms.common.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data @Builder
public class EmployeeDependentResponse {
    private Long id;
    private Long employeeId;
    private Long relationId;
    private String relationName;
    private String relationNameAr;
    private String firstName;
    private String middleName;
    private String lastName;
    private String firstNameAr;
    private String middleNameAr;
    private String lastNameAr;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String birthPlace;
    private Boolean isAlive;
    private String phone;
    private String address;
    private Boolean isActive;
    private Long version;
}
