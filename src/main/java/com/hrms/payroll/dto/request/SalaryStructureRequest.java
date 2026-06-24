package com.hrms.payroll.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SalaryStructureRequest(

        @NotBlank(message = "Structure code is required")
        @Size(max = 30)
        String structureCode,

        @NotBlank(message = "Structure name is required")
        @Size(max = 100)
        String structureName,

        String description,

        @NotEmpty(message = "At least one salary component is required")
        @Valid
        List<SalaryStructureItemRequest> items
) {}
