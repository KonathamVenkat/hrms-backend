-- Optimistic locking (VERSION) and "who changed it" audit columns.
-- Hibernate runs with ddl-auto=validate, so this MUST be applied to the HRMS schema
-- BEFORE deploying the matching backend build, or the app will fail to start.
-- Safe to run on a schema with existing rows: every existing row starts at version 0.

-- A second writer holding stale data now gets HTTP 409 instead of silently overwriting the first.
ALTER TABLE HRMS.EMPLOYEES              ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
ALTER TABLE HRMS.EMPLOYEE_ADDRESSES     ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
ALTER TABLE HRMS.EMPLOYEE_IDENTITY_INFO ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
ALTER TABLE HRMS.LEAVE_BALANCES         ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);

-- Address and identity rows had CREATED_AT / UPDATED_AT but not who made the change.
-- Existing rows keep NULL here (the author is unknown).
ALTER TABLE HRMS.EMPLOYEE_ADDRESSES     ADD (CREATED_BY VARCHAR2(36), UPDATED_BY VARCHAR2(36));
ALTER TABLE HRMS.EMPLOYEE_IDENTITY_INFO ADD (CREATED_BY VARCHAR2(36), UPDATED_BY VARCHAR2(36));

-- Requests that managers approve or reject: a double click, or two approvers, can no longer both win.
ALTER TABLE HRMS.LEAVE_REQUESTS            ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
ALTER TABLE HRMS.OVERTIME_REQUESTS         ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
ALTER TABLE HRMS.ATTENDANCE_REGULARIZATION ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);
