package com.hrms.employee.dto.response;

import java.math.BigDecimal;

/** One step of the pay scale for the grade/increment dropdowns: the starting scale in effect today and since when. */
public record GradeScaleStepResponse(
    Long gradeId,
    String gradeCode,
    String gradeName,
    String gradeNameAr,
    Integer incrementNo,
    BigDecimal startingScale,
    String effectiveFrom
) {}
