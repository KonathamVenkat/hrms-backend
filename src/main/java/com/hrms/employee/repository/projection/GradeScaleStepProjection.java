package com.hrms.employee.repository.projection;

import java.math.BigDecimal;

/** One grade and increment of the pay scale with the starting scale in effect on the asked date. */
public interface GradeScaleStepProjection {
    Long       getGradeId();
    String     getGradeCode();
    String     getGradeName();
    String     getGradeNameAr();
    Integer    getIncrementNo();
    BigDecimal getStartingScale();
    String     getEffectiveFrom();
}
