-- Audit trail: who did what, to which record, and when.
-- Hibernate runs with ddl-auto=validate, so this MUST be applied to the HRMS schema
-- BEFORE deploying the matching backend build, or the app will fail to start.
-- Append-only by design: the application only ever inserts. Do not grant UPDATE or DELETE on it
-- to the application user if your DBA can enforce that.

CREATE TABLE HRMS.AUDIT_EVENTS (
    EVENT_ID    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EVENT_TIME  TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    ACTOR       VARCHAR2(100)  NOT NULL,   -- username, or SYSTEM for scheduled jobs
    ACTION      VARCHAR2(60)   NOT NULL,   -- e.g. LOGIN_FAILED, LEAVE_APPROVED
    TARGET_TYPE VARCHAR2(40),              -- e.g. EMPLOYEE, LEAVE_REQUEST
    TARGET_ID   VARCHAR2(64),
    DETAIL      VARCHAR2(1000)             -- short context; never a password, token or identity number
);

CREATE INDEX IDX_AUDIT_TARGET ON HRMS.AUDIT_EVENTS (TARGET_TYPE, TARGET_ID);
CREATE INDEX IDX_AUDIT_ACTOR  ON HRMS.AUDIT_EVENTS (ACTOR, EVENT_TIME);
CREATE INDEX IDX_AUDIT_TIME   ON HRMS.AUDIT_EVENTS (EVENT_TIME);
