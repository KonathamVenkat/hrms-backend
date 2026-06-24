package com.hrms.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base class for all JPA entities requiring audit trail fields.
 *
 * <p>Spring Data JPA's {@link AuditingEntityListener} automatically populates:
 * <ul>
 *   <li>{@code createdBy} and {@code updatedBy} via {@link AuditorAwareImpl} (uses JWT principal)</li>
 *   <li>{@code createdAt} and {@code updatedAt} via the auditing infrastructure</li>
 * </ul>
 *
 * <p>Matches Oracle columns: CREATED_BY (VARCHAR2 36), CREATED_AT (TIMESTAMP),
 * UPDATED_BY (VARCHAR2 36), UPDATED_AT (TIMESTAMP).</p>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor @AllArgsConstructor @SuperBuilder
public abstract class Auditable {

    /**
     * UUID or username of the user who created the record.
     * Maps to Oracle: CREATED_BY VARCHAR2(36)
     */
    @CreatedBy
    @Column(name = "CREATED_BY", length = 36, updatable = false)
    private String createdBy;

    /**
     * Timestamp when the record was created.
     * Maps to Oracle: CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
     */
    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * UUID or username of the user who last modified the record.
     * Maps to Oracle: UPDATED_BY VARCHAR2(36)
     */
    @LastModifiedBy
    @Column(name = "UPDATED_BY", length = 36)
    private String updatedBy;

    /**
     * Timestamp of the last modification.
     * Maps to Oracle: UPDATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
     */
    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}
