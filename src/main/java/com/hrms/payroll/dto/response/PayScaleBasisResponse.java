package com.hrms.payroll.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the pay scale decides for an employee's salary on a date. {@code graded} false: the employee's current job has
 * no grade, so the structure is chosen and the basic typed by hand (the other fields are then absent). Graded: the
 * grade's structure (absent when none is linked yet) and the starting scale on {@code onDate} (absent when the scale
 * has no amount then); a salary cannot be assigned until both are there.
 */
public record PayScaleBasisResponse(
        boolean    graded,
        LocalDate  onDate,
        Long       gradeId,
        String     gradeName,
        String     gradeNameAr,
        Integer    incrementNo,
        Long       structureId,
        String     structureName,
        BigDecimal basicSalary
) {}
