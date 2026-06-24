package com.hrms.payroll.entity;

import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A named salary package that groups multiple components.
 * e.g. "Grade A - Executive Package", "Grade B - Staff Package"
 * Each structure has a list of SalaryStructureItems defining
 * which components are included and at what value/percentage.
 */
@Entity
@Table(name = "SALARY_STRUCTURES", schema = "HRMS")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SalaryStructure extends Auditable {

    @Id
    @Column(name = "STRUCTURE_ID", nullable = false)
    private Long structureId;

    @Column(name = "STRUCTURE_CODE", nullable = false, length = 30)
    private String structureCode;

    @Column(name = "STRUCTURE_NAME", nullable = false, length = 100)
    private String structureName;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    // Items loaded when building the full structure response
    @OneToMany(mappedBy = "structureId", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SalaryStructureItem> items = new ArrayList<>();
}
