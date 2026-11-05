-- V21: optimistic-lock version on EMPLOYEE_JOB_DETAILS, so a Job Details screen opened before someone else saved cannot silently
-- overwrite that save. Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v21.sql. Rollback: db/rollback_V21.sql.
-- Additive: every existing row starts at version 0.
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS ADD (VERSION NUMBER(10,0) DEFAULT 0 NOT NULL);
