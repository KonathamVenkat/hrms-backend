package com.hrms.leave.dto.request;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveFilterRequest {

    private String  status;
    private String  leaveTypeCode;
    private Integer year;
    private Long    employeeId;      // added for HR filter by employee

    @Builder.Default private Integer page    = 0;
    @Builder.Default private Integer size    = 10;
    @Builder.Default private String  sortBy  = "createdAt";
    @Builder.Default private String  sortDir = "desc";
}
