package com.hrms.employee.repository;
import com.hrms.employee.entity.EmployeeDocument;
import com.hrms.employee.repository.projection.EmployeeDocumentProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeDocumentRepository
        extends JpaRepository<EmployeeDocument, Long> {

    // ── All documents for an employee with type info ──────────
    @Query(value = """
        SELECT
            ed.DOCUMENT_ID           AS documentId,
            ed.EMPLOYEE_ID           AS employeeId,
            ed.DOC_TYPE_ID           AS docTypeId,
            dt.DOC_TYPE_CODE         AS docTypeCode,
            dt.DOC_TYPE_NAME         AS docTypeName,
            dt.CATEGORY              AS category,
            dt.HAS_EXPIRY            AS hasExpiry,
            ed.DOCUMENT_NAME         AS documentName,
            ed.ORIGINAL_FILE_NAME    AS originalFileName,
            ed.FILE_SIZE             AS fileSize,
            ed.FILE_EXTENSION        AS fileExtension,
            ed.DOCUMENT_NUMBER       AS documentNumber,
            TO_CHAR(ed.ISSUE_DATE,   'YYYY-MM-DD') AS issueDate,
            TO_CHAR(ed.EXPIRY_DATE,  'YYYY-MM-DD') AS expiryDate,
            ed.ISSUED_BY             AS issuedBy,
            ed.NOTES                 AS notes,
            ed.IS_VERIFIED           AS isVerified,
            ed.VERIFIED_BY           AS verifiedBy,
            TO_CHAR(ed.VERIFIED_AT,  'YYYY-MM-DD HH24:MI:SS') AS verifiedAt,
            ed.IS_ACTIVE             AS isActive,
            ed.UPLOADED_BY           AS uploadedBy,
            TO_CHAR(ed.CREATED_AT,   'YYYY-MM-DD HH24:MI:SS') AS createdAt
        FROM HRMS.EMPLOYEE_DOCUMENTS ed
        JOIN HRMS.DOCUMENT_TYPES     dt ON dt.DOC_TYPE_ID = ed.DOC_TYPE_ID
        WHERE ed.EMPLOYEE_ID = :employeeId
          AND ed.IS_ACTIVE   = 1
        ORDER BY dt.SORT_ORDER ASC, ed.CREATED_AT DESC
        """, nativeQuery = true)
    List<EmployeeDocumentProjection> findActiveByEmployee(
            @Param("employeeId") Long employeeId);

    // ── Single document with type info ────────────────────────
    @Query(value = """
        SELECT
            ed.DOCUMENT_ID           AS documentId,
            ed.EMPLOYEE_ID           AS employeeId,
            ed.DOC_TYPE_ID           AS docTypeId,
            dt.DOC_TYPE_CODE         AS docTypeCode,
            dt.DOC_TYPE_NAME         AS docTypeName,
            dt.CATEGORY              AS category,
            dt.HAS_EXPIRY            AS hasExpiry,
            ed.DOCUMENT_NAME         AS documentName,
            ed.ORIGINAL_FILE_NAME    AS originalFileName,
            ed.FILE_SIZE             AS fileSize,
            ed.FILE_EXTENSION        AS fileExtension,
            ed.DOCUMENT_NUMBER       AS documentNumber,
            TO_CHAR(ed.ISSUE_DATE,   'YYYY-MM-DD') AS issueDate,
            TO_CHAR(ed.EXPIRY_DATE,  'YYYY-MM-DD') AS expiryDate,
            ed.ISSUED_BY             AS issuedBy,
            ed.NOTES                 AS notes,
            ed.IS_VERIFIED           AS isVerified,
            ed.VERIFIED_BY           AS verifiedBy,
            TO_CHAR(ed.VERIFIED_AT,  'YYYY-MM-DD HH24:MI:SS') AS verifiedAt,
            ed.IS_ACTIVE             AS isActive,
            ed.UPLOADED_BY           AS uploadedBy,
            TO_CHAR(ed.CREATED_AT,   'YYYY-MM-DD HH24:MI:SS') AS createdAt
        FROM HRMS.EMPLOYEE_DOCUMENTS ed
        JOIN HRMS.DOCUMENT_TYPES     dt ON dt.DOC_TYPE_ID = ed.DOC_TYPE_ID
        WHERE ed.DOCUMENT_ID = :documentId
          AND ed.EMPLOYEE_ID = :employeeId
          AND ed.IS_ACTIVE   = 1
        """, nativeQuery = true)
    Optional<EmployeeDocumentProjection> findByDocumentIdAndEmployeeId(
            @Param("documentId")  Long documentId,
            @Param("employeeId")  Long employeeId);

    // ── Existence check ───────────────────────────────────────
    boolean existsByDocumentIdAndEmployeeId(Long documentId, Long employeeId);

    // ── Count by type ─────────────────────────────────────────
    long countByEmployeeIdAndDocTypeIdAndIsActive(
            Long employeeId, Long docTypeId, Integer isActive);
}
