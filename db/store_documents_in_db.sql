-- Employee documents and leave attachments are stored in the database (no upload folder).
-- Hibernate runs with ddl-auto=validate, so this MUST be applied to the HRMS schema
-- BEFORE deploying the matching backend build, or the app will fail to start.
-- Run after leave_request_attachment.sql.

-- 1. File bytes, one row per document / attachment (kept apart from the metadata rows so
--    listing and editing never read the BLOB).
CREATE TABLE HRMS.EMPLOYEE_DOCUMENT_CONTENT (
    DOCUMENT_ID  NUMBER       NOT NULL,
    CONTENT      BLOB         NOT NULL,
    CONSTRAINT PK_EMP_DOC_CONTENT PRIMARY KEY (DOCUMENT_ID),
    CONSTRAINT FK_EMP_DOC_CONTENT_DOC FOREIGN KEY (DOCUMENT_ID)
        REFERENCES HRMS.EMPLOYEE_DOCUMENTS (DOCUMENT_ID)
);

CREATE TABLE HRMS.LEAVE_REQUEST_ATTACHMENT_CONTENT (
    LEAVE_REQ_ID NUMBER       NOT NULL,
    CONTENT      BLOB         NOT NULL,
    CONSTRAINT PK_LEAVE_ATT_CONTENT PRIMARY KEY (LEAVE_REQ_ID),
    CONSTRAINT FK_LEAVE_ATT_CONTENT_REQ FOREIGN KEY (LEAVE_REQ_ID)
        REFERENCES HRMS.LEAVE_REQUESTS (LEAVE_REQ_ID)
);

-- 2. The file-path columns are no longer written. The entity does not map FILE_PATH any more,
--    so it must accept NULL.
ALTER TABLE HRMS.EMPLOYEE_DOCUMENTS MODIFY (FILE_PATH NULL);

-- 3. Only if leave_request_attachment.sql was applied BEFORE this change (a fresh schema no
--    longer gets this column): it is unused now. Optional clean-up:
-- ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN ATTACHMENT_PATH;

-- Rows uploaded before this change have a path but no content row: downloading them returns
-- "not found". Re-upload them, or drop the test rows.
