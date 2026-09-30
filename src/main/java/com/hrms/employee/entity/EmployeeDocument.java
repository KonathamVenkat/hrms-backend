package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity for HRMS.EMPLOYEE_DOCUMENTS table.
 *
 * Stores metadata for uploaded employee documents.
 * Each document is linked to a DocumentType (Phase 1 admin config).
 * The file bytes live in the database too, in {@link EmployeeDocumentContent}.
 *
 * An employee can have multiple documents of the same type
 * (e.g. multiple passports over time).
 */
@Entity
@Table(name = "EMPLOYEE_DOCUMENTS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "emp_doc_seq")
    @SequenceGenerator(
        name           = "emp_doc_seq",
        sequenceName   = "HRMS.SEQ_EMPLOYEE_DOCUMENTS",
        allocationSize = 1
    )
    @Column(name = "DOCUMENT_ID", nullable = false)
    private Long documentId;

    // ── Employee ──────────────────────────────────────────────
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    // ── Document Type (FK to DOCUMENT_TYPES) ─────────────────
    @Column(name = "DOC_TYPE_ID", nullable = false)
    private Long docTypeId;

    // ── File metadata ─────────────────────────────────────────
    @Column(name = "DOCUMENT_NAME", nullable = false, length = 300)
    private String documentName;

    @Column(name = "ORIGINAL_FILE_NAME", nullable = false, length = 300)
    private String originalFileName;

    /** File size in bytes */
    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    /** e.g. PDF, JPG, PNG */
    @Column(name = "FILE_EXTENSION", nullable = false, length = 10)
    private String fileExtension;

    // ── Document details ──────────────────────────────────────
    @Column(name = "DOCUMENT_NUMBER", length = 100)
    private String documentNumber;

    @Column(name = "ISSUE_DATE")
    private LocalDate issueDate;

    @Column(name = "EXPIRY_DATE")
    private LocalDate expiryDate;

    /** e.g. Royal Oman Police, Ministry of Manpower */
    @Column(name = "ISSUED_BY", length = 200)
    private String issuedBy;

    @Column(name = "NOTES", length = 500)
    private String notes;

    // ── Verification ──────────────────────────────────────────
    @Column(name = "IS_VERIFIED", nullable = false)
    @Builder.Default
    private Integer isVerified = 0;

    @Column(name = "VERIFIED_BY", length = 50)
    private String verifiedBy;

    @Column(name = "VERIFIED_AT")
    private LocalDateTime verifiedAt;

    // ── Status ────────────────────────────────────────────────
    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    // ── Audit ─────────────────────────────────────────────────
    @Column(name = "UPLOADED_BY", length = 50)
    private String uploadedBy;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
