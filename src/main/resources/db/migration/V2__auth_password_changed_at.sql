-- F7: access tokens issued before the last password change/reset are rejected.
-- Hibernate runs with ddl-auto=validate, so apply this to the HRMS schema BEFORE deploying
-- the matching backend build, or the app will fail to start.
-- NULL = password never changed since this column existed (no token is rejected).
ALTER TABLE HRMS.AUTH_USERS ADD (
    PASSWORD_CHANGED_AT TIMESTAMP(6)
);
