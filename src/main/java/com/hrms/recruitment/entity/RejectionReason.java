package com.hrms.recruitment.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import com.hrms.recruitment.enums.ReasonScope;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Why an application or an offer was turned down (table REJECTION_REASONS). */
@Entity
@Table(name = "REJECTION_REASONS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RejectionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rec_reject_reasons_seq")
    @SequenceGenerator(name = "rec_reject_reasons_seq", sequenceName = "HRMS.SEQ_REC_REJECT_REASONS", allocationSize = 1)
    @Column(name = "REASON_ID", nullable = false)
    private Long id;

    @Column(name = "REASON_CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "REASON_NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "REASON_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(300)")
    private String nameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "APPLIES_TO", nullable = false, length = 20)
    private ReasonScope appliesTo;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

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
