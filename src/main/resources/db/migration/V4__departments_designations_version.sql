-- D-5: optimistic locking for Departments and Designations (same as EMPLOYEES etc. in
-- db/optimistic_locking_and_audit.sql). Hibernate runs with ddl-auto=validate, so apply this to the
-- HRMS schema BEFORE deploying the matching backend build, or the app will fail to start.
-- Every existing row starts at version 0. A second admin saving a stale screen then gets HTTP 409.
ALTER TABLE HRMS.DEPARTMENTS  ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
ALTER TABLE HRMS.DESIGNATIONS ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
