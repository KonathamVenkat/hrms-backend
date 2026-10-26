package com.hrms.workflow.entity;

import com.hrms.workflow.enums.HistoryAction;
import com.hrms.workflow.enums.RequestType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** Append-only: the code never updates or deletes a row. */
@Entity
@Immutable
@Table(name = "REQUEST_HISTORY", schema = "HRMS")
@Getter @Setter @NoArgsConstructor
public class RequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "request_history_seq")
    @SequenceGenerator(name = "request_history_seq", sequenceName = "HRMS.SEQ_REQUEST_HISTORY", allocationSize = 1)
    @Column(name = "HISTORY_ID", nullable = false)
    private Long historyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "REQUEST_TYPE", nullable = false, length = 30)
    private RequestType requestType;

    @Column(name = "REQUEST_ID", nullable = false)
    private Long requestId;

    @Column(name = "STEP_KEY", length = 30)
    private String stepKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTION", nullable = false, length = 20)
    private HistoryAction action;

    /** Null for system actions (AUTO_ROUTED) and for a SYSTEM_ADMIN account with no employee record. */
    @Column(name = "ACTOR_EMPLOYEE_ID")
    private Long actorEmployeeId;

    @Column(name = "ACTOR_ROLE", length = 30)
    private String actorRole;

    @Column(name = "COMMENT_TEXT", length = 500)
    private String comment;

    @Column(name = "REASON_CODE", length = 30)
    private String reasonCode;

    @Column(name = "ACTED_AT", nullable = false)
    private LocalDateTime actedAt;

    @PrePersist
    void stampActedAt() {
        if (actedAt == null) {
            actedAt = LocalDateTime.now();
        }
    }
}
