package com.hrms.workflow.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.common.util.BooleanToNumberConverter;
import com.hrms.workflow.enums.ApproverType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "WORKFLOW_STEPS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor
public class WorkflowStep extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wf_steps_seq")
    @SequenceGenerator(name = "wf_steps_seq", sequenceName = "HRMS.SEQ_WORKFLOW_STEPS", allocationSize = 1)
    @Column(name = "STEP_ID", nullable = false)
    private Long stepId;

    @Column(name = "DEFINITION_ID", nullable = false)
    private Long definitionId;

    @Column(name = "STEP_KEY", nullable = false, length = 30)
    private String stepKey;

    @Column(name = "POSITION_NO", nullable = false)
    private Integer positionNo;

    @Column(name = "LABEL_EN", nullable = false, length = 100)
    private String labelEn;

    @Column(name = "LABEL_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String labelAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "APPROVER_TYPE", nullable = false, length = 30)
    private ApproverType approverType;

    /** Only for approverType ROLE (HR_ADMIN, HR_MANAGER, GENERAL_MANAGER); null otherwise (CHECK in V10). */
    @Column(name = "APPROVER_ROLE", length = 30)
    private String approverRole;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "REQUIRES_DOCUMENT_REVIEW", nullable = false)
    private Boolean requiresDocumentReview = false;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "PICKS_TARGET_DEPARTMENT", nullable = false)
    private Boolean picksTargetDepartment = false;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
