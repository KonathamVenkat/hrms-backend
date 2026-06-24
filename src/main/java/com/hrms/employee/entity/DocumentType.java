package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name   = "DOCUMENT_TYPES",
    schema = "HRMS",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_DOC_TYPE_CODE", columnNames = "DOC_TYPE_CODE"),
        @UniqueConstraint(name = "UQ_DOC_TYPE_NAME", columnNames = "DOC_TYPE_NAME")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "doc_type_seq")
    @SequenceGenerator(
        name = "doc_type_seq",
        sequenceName = "HRMS.SEQ_DOCUMENT_TYPES",
        allocationSize = 1
    )
    @Column(name = "DOC_TYPE_ID", nullable = false)
    private Long docTypeId;

    @Column(name = "DOC_TYPE_CODE", nullable = false, length = 30)
    private String docTypeCode;

    @Column(name = "DOC_TYPE_NAME", nullable = false, length = 100)
    private String docTypeName;

    @Column(name = "DOC_TYPE_NAME_AR", nullable = false,
            columnDefinition = "NVARCHAR2(200)")
    private String docTypeNameAr;

    /** IDENTITY | CONTRACT | EDUCATION | CERTIFICATE | MEDICAL | FINANCIAL | OTHER */
    @Column(name = "CATEGORY", nullable = false, length = 30)
    @Builder.Default
    private String category = "OTHER";

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** Mandatory upload for all employees */
    @Column(name = "IS_MANDATORY", nullable = false)
    @Builder.Default
    private Integer isMandatory = 0;

    /** Document expires — e.g. passport, visa */
    @Column(name = "HAS_EXPIRY", nullable = false)
    @Builder.Default
    private Integer hasExpiry = 0;

    /** Days before expiry to send notification */
    @Column(name = "EXPIRY_NOTICE_DAYS", nullable = false)
    @Builder.Default
    private Integer expiryNoticeDays = 30;

    /** Comma-separated e.g. "PDF,JPG,PNG" */
    @Column(name = "ALLOWED_EXTENSIONS", length = 200)
    @Builder.Default
    private String allowedExtensions = "PDF,JPG,PNG";

    /** Maximum upload size in MB */
    @Column(name = "MAX_FILE_SIZE_MB", nullable = false)
    @Builder.Default
    private Integer maxFileSizeMb = 5;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    @Column(name = "SORT_ORDER", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
