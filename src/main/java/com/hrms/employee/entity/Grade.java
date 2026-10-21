package com.hrms.employee.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Grade master (table GRADES); columns carry the table name as prefix, the Java fields stay plain. */
@Entity
@Table(name = "GRADES", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "grades_seq")
    @SequenceGenerator(name = "grades_seq", sequenceName = "HRMS.SEQ_GRADES", allocationSize = 1)
    @Column(name = "GRADE_ID", nullable = false)
    private Long id;

    @Column(name = "GRADES_CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "GRADES_NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "GRADES_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String nameAr;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

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
