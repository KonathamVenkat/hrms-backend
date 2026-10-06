package com.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One row of the audit trail. Append-only: rows are inserted, never changed. Maps to HRMS.AUDIT_EVENTS. */
@Entity
@Table(name = "AUDIT_EVENTS", schema = "HRMS")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EVENT_ID")
    private Long id;

    @Column(name = "EVENT_TIME", nullable = false, updatable = false)
    private LocalDateTime eventTime;

    @Column(name = "ACTOR", nullable = false, updatable = false, length = 100)
    private String actor;

    @Column(name = "ACTION", nullable = false, updatable = false, length = 60)
    private String action;

    @Column(name = "TARGET_TYPE", updatable = false, length = 40)
    private String targetType;

    @Column(name = "TARGET_ID", updatable = false, length = 64)
    private String targetId;

    @Column(name = "DETAIL", updatable = false, length = 1000)
    private String detail;
}
