-- Force-change-password flag (change-password / HR reset / first sign-in).
-- Hibernate runs with ddl-auto=validate, so apply this to the HRMS schema BEFORE deploying
-- the matching backend build, or the app will fail to start.
-- Existing accounts default to 0 (no forced change).
ALTER TABLE HRMS.AUTH_USERS ADD (
    MUST_CHANGE_PASSWORD NUMBER(1) DEFAULT 0 NOT NULL
);
