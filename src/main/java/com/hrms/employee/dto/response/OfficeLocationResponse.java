package com.hrms.employee.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder
public class OfficeLocationResponse {
    private Long       locationId;
    private String     locationCode;
    private String     locationName;
    private String     locationNameAr;
    private String     locationType;
    private String     addressLine1;
    private String     addressLine2;
    private String     city;
    private String     stateProvince;
    private String     country;
    private String     postalCode;
    private String     phone;
    private String     email;
    private String     timezone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean    isActive;
    private Integer    sortOrder;
    private String     createdBy;
    private String     createdAt;
    private String     updatedAt;
}
