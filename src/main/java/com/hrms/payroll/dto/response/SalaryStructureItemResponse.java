package com.hrms.payroll.dto.response;

import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// ── SalaryStructureItemResponse ───────────────────────────
public record SalaryStructureItemResponse(
        Long              itemId,
        Long              componentId,
        String            componentCode,
        String            componentName,
        String            componentNameAr,
        ComponentType     componentType,
        CalculationType   effectiveCalcType,   // override or component default
        BigDecimal        amount,
        BigDecimal        percentage,
        Integer           sortOrder
) {}
