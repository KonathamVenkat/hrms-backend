-- V25: the CEO role. Decides the General Manager's own requests (the General Manager decides those of HR_ADMIN and
-- HR_MANAGER); otherwise it is an ordinary employee. Same shape as the GENERAL_MANAGER role in V10: a ROLES row and a place
-- in the AUTH_USERS.ROLE check (the check is dropped and recreated with every role, existing 11 + CEO).
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v25.sql. Rollback: db/rollback_V25.sql. Menu: db/approver_roles_menu.sql (run after the backend is deployed).
-- Create the CEO's employee record and login afterwards from the Employee screen (role CEO).
INSERT INTO HRMS.ROLES (ROLE_NAME, DESCRIPTION)
SELECT 'CEO', 'Decides the general manager''s own requests'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.ROLES WHERE ROLE_NAME = 'CEO');

-- Brief DDL window: between the DROP and the ADD of the check, AUTH_USERS.ROLE is unconstrained for a few
-- milliseconds. Run V25 with the backend stopped (as for every migration).
DECLARE
BEGIN
  FOR c IN (SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS
             WHERE OWNER = 'HRMS' AND TABLE_NAME = 'AUTH_USERS' AND CONSTRAINT_TYPE = 'C'
               AND CONSTRAINT_NAME = 'CK_AUTH_USERS_ROLE') LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.AUTH_USERS DROP CONSTRAINT "' || c.CONSTRAINT_NAME || '"';
  END LOOP;
  EXECUTE IMMEDIATE q'[ALTER TABLE HRMS.AUTH_USERS ADD CONSTRAINT CK_AUTH_USERS_ROLE CHECK ("ROLE" IN
    ('SYSTEM_ADMIN','HR_ADMIN','HR_MANAGER','GENERAL_MANAGER','CEO','EMPLOYEE','REPORTING_MANAGER',
     'ATTENDANCE_OFFICER','PAYROLL_OFFICER','PAYROLL_APPROVER','FINANCE_USER','AUDITOR'))]';
END;
/

COMMIT;
