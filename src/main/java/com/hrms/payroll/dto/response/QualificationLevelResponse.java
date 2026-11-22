package com.hrms.payroll.dto.response;

import java.math.BigDecimal;

/** A qualification level as the masters screen and the Assign Salary dropdown show it. */
public record QualificationLevelResponse(
        Long       id,
        String     code,
        String     name,
        String     nameAr,
        BigDecimal monthlyAmount,
        Integer    sortOrder,
        Boolean    isActive,
        String     createdAt,
        String     updatedAt,
        Long       version
) {}
