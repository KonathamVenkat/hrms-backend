package com.hrms.workflow.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.workflow.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One published version of a request type's flow. At most one ACTIVE per type (unique index in V10). */
@Entity
@Table(name = "WORKFLOW_DEFINITIONS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor
public class WorkflowDefinition extends Auditable {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wf_definitions_seq")
    @SequenceGenerator(name = "wf_definitions_seq", sequenceName = "HRMS.SEQ_WORKFLOW_DEFINITIONS", allocationSize = 1)
    @Column(name = "DEFINITION_ID", nullable = false)
    private Long definitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "REQUEST_TYPE", nullable = false, length = 30)
    private RequestType requestType;

    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Column(name = "STATUS", nullable = false, length = 10)
    private String status = STATUS_DRAFT;

    @Column(name = "FLOWABLE_DEPLOYMENT_ID", length = 64)
    private String flowableDeploymentId;

    @Column(name = "FLOWABLE_PROCESS_DEFINITION_ID", length = 128)
    private String flowableProcessDefinitionId;

    @Column(name = "PUBLISHED_BY", length = 50)
    private String publishedBy;

    @Column(name = "PUBLISHED_AT")
    private LocalDateTime publishedAt;

    /** Optimistic lock (two admins publishing at once). */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
