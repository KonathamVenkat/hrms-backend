package com.hrms.employee.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Nationality master (table NATIONALITIES); columns carry the table name as prefix, the Java fields stay plain. */
@Entity
@Table(name = "NATIONALITIES", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Nationality {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nationalities_seq")
    @SequenceGenerator(name = "nationalities_seq", sequenceName = "HRMS.SEQ_NATIONALITIES", allocationSize = 1)
    @Column(name = "NATIONALITY_ID", nullable = false)
    private Long id;

    @Column(name = "NATIONALITIES_CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "NATIONALITIES_NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "NATIONALITIES_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String nameAr;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    /** Optimistic lock: two admins saving the same row no longer overwrite each other. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    // Both timestamps default to SYSTIMESTAMP in the table; the service sets UPDATED_AT on change.
    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false)
    private LocalDateTime updatedAt;
}
