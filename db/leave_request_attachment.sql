-- Supporting-document attachment for leave requests (LeaveType.requiresDocument).
-- Hibernate runs with ddl-auto=validate, so this MUST be applied to the HRMS schema
-- BEFORE deploying the matching backend build, or the app will fail to start.
-- (The file bytes live in LEAVE_REQUEST_ATTACHMENT_CONTENT, created by store_documents_in_db.sql.)
ALTER TABLE HRMS.LEAVE_REQUESTS ADD (
    ATTACHMENT_NAME  VARCHAR2(255),
    ATTACHMENT_SIZE  NUMBER(12)
);

-- Per-leave-type upload limits (admin > leave types). NULL = use the defaults (5 MB, pdf/jpg/jpeg/png).
ALTER TABLE HRMS.LEAVE_TYPES ADD (
    DOC_MAX_FILE_SIZE_MB   NUMBER(3),
    DOC_ALLOWED_EXTENSIONS VARCHAR2(100)
);
