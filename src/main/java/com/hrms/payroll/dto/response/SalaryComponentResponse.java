package com.hrms.payroll.dto.response;

import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// ── SalaryComponentResponse ───────────────────────────────
public record SalaryComponentResponse(
        Long              componentId,
        String            componentCode,
        String            componentName,
        String            componentNameAr,
        ComponentType     componentType,
        String            componentTypeLabel,
        CalculationType   calcType,
        String            calcTypeLabel,
        BigDecimal        defaultValue,
        Boolean           isTaxable,
        Boolean           isNssfApplicable,
        String            description,
        Integer           sortOrder,
        Boolean           isActive,
        LocalDateTime     createdAt,
        LocalDateTime     updatedAt
) {}
