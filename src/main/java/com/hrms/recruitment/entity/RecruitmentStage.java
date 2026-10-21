package com.hrms.recruitment.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import com.hrms.recruitment.enums.StageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A step of the hiring pipeline (table RECRUITMENT_STAGES). Code branches on {@link #stageType}, never on the name. */
@Entity
@Table(name = "RECRUITMENT_STAGES", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecruitmentStage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rec_stages_seq")
    @SequenceGenerator(name = "rec_stages_seq", sequenceName = "HRMS.SEQ_REC_STAGES", allocationSize = 1)
    @Column(name = "STAGE_ID", nullable = false)
    private Long id;

    @Column(name = "STAGE_CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "STAGE_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "STAGE_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String nameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "STAGE_TYPE", nullable = false, length = 20)
    private StageType stageType;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    /** System stages are the ones the code relies on: they cannot be deleted, deactivated, re-coded or re-typed. */
    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_SYSTEM", nullable = false, updatable = false)
    private Boolean isSystem;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false)
    private LocalDateTime updatedAt;
}
