package com.hrms.employee.dto.response;

import lombok.*;

@Data @Builder
public class EmployeeAddressResponse {
    private Long    employeeAddressesId;
    private Long    employeeId;
    private String  addressType;
    private String  addressLine1;
    private String  addressLine2;
    private String  city;
    private String  stateProvince;
    private String  country;
    private String  postalCode;
    private Boolean isPrimary;
    private Boolean isActive;
    private String  createdAt;
    private String  updatedAt;
}
