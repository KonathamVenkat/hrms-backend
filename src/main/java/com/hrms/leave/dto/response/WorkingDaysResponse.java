package com.hrms.leave.dto.response;

/** Leave days a date range costs one employee, by their shift and the public holidays. */
public record WorkingDaysResponse(double workingDays) {}
