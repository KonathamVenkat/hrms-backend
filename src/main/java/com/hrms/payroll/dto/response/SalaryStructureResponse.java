package com.hrms.payroll.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record SalaryStructureResponse(
        Long                              structureId,
        String                            structureCode,
        String                            structureName,
        String                            description,
        Boolean                           isActive,
        List<SalaryStructureItemResponse> items,
        int                               employeeCount,   // how many employees use this structure
        LocalDateTime                     createdAt,
        LocalDateTime                     updatedAt
) {}
