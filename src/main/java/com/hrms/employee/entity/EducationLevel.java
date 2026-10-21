package com.hrms.employee.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Education level master (table EDUCATION_LEVELS); columns carry the table name as prefix, the Java fields stay plain. */
@Entity
@Table(name = "EDUCATION_LEVELS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EducationLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "education_levels_seq")
    @SequenceGenerator(name = "education_levels_seq", sequenceName = "HRMS.SEQ_EDUCATION_LEVELS", allocationSize = 1)
    @Column(name = "EDUCATION_LEVEL_ID", nullable = false)
    private Long id;

    @Column(name = "EDUCATION_LEVELS_CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "EDUCATION_LEVELS_NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "EDUCATION_LEVELS_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
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
