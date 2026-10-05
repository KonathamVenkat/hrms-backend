-- The statutory social-security flag on salary components was named after Oman's PASI.
-- South Sudan's fund is the NSSF, so the column is renamed to match the application code.
-- Hibernate runs with ddl-auto=validate, so this MUST be applied to the HRMS schema
-- BEFORE deploying the matching backend build, or the app will fail to start.
-- Values are untouched; only the column name changes.
ALTER TABLE HRMS.SALARY_COMPONENTS RENAME COLUMN IS_PASI_APPLICABLE TO IS_NSSF_APPLICABLE;
