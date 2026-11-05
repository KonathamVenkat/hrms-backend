-- V22: one national ID, passport, visa and work-permit number per employee, in the database as well as in the service.
-- Until now only the service checked, with the raw text, so " ab123" and "AB123" slipped past and two simultaneous saves could
-- both pass. The indexes compare the number the way the service does: trimmed and upper-cased. Empty values are ignored.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v22.sql. Rollback: db/rollback_V22.sql.
-- BEFORE running, look for numbers that two employees already share; the index cannot be built while any exist:
--   SELECT UPPER(TRIM(NATIONAL_ID)), COUNT(*) FROM HRMS.EMPLOYEE_IDENTITY_INFO WHERE TRIM(NATIONAL_ID) IS NOT NULL
--    GROUP BY UPPER(TRIM(NATIONAL_ID)) HAVING COUNT(*) > 1;   (repeat for PASSPORT_NUMBER, VISA_NUMBER, WORK_PERMIT_NUMBER)
-- Fix each pair with HR (one of them is a typing mistake), then run this script.
CREATE UNIQUE INDEX HRMS.UQ_EII_NATIONAL_ID ON HRMS.EMPLOYEE_IDENTITY_INFO (UPPER(TRIM(NATIONAL_ID)));
CREATE UNIQUE INDEX HRMS.UQ_EII_PASSPORT    ON HRMS.EMPLOYEE_IDENTITY_INFO (UPPER(TRIM(PASSPORT_NUMBER)));
CREATE UNIQUE INDEX HRMS.UQ_EII_VISA_NUMBER ON HRMS.EMPLOYEE_IDENTITY_INFO (UPPER(TRIM(VISA_NUMBER)));
CREATE UNIQUE INDEX HRMS.UQ_EII_WORK_PERMIT ON HRMS.EMPLOYEE_IDENTITY_INFO (UPPER(TRIM(WORK_PERMIT_NUMBER)));
