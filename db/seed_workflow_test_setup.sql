-- DEVELOPMENT DATA (2026-11-23): what the leave approval-workflow end-to-end test needs on the dev database.
--  1. Juba Head Office (JUBA_HO) gets a branch manager: the head of General Administration (DEP-061). Only if empty.
--  2. 2026 leave balances (ANNUAL 21, CASUAL 6, SICK 15 days, like the existing employees) for every active employee
--     with a current job who has none for that type and year.
--  3. The test General Manager (anil.mehta) and CEO (vikram.rathore) logins get the shared test password Welcome123 with
--     no forced change (owner's choice; HR_ADMIN logins are not touched).
-- Never run on production. Safe to run twice. Undo: db/seed_workflow_test_setup_rollback.sql
UPDATE HRMS.COMPANY_BRANCHES b
   SET b.BRANCH_MANAGER_EMPLOYEE_ID = (SELECT d.HEAD_EMPLOYEE_ID FROM HRMS.DEPARTMENTS d WHERE d.CODE = 'DEP-061')
 WHERE b.COMPANY_BRANCHES_CODE = 'JUBA_HO'
   AND b.BRANCH_MANAGER_EMPLOYEE_ID IS NULL
   AND EXISTS (SELECT 1 FROM HRMS.DEPARTMENTS d WHERE d.CODE = 'DEP-061' AND d.HEAD_EMPLOYEE_ID IS NOT NULL);
-- Expect: 1 row (0 on a second run).

INSERT INTO HRMS.LEAVE_BALANCES (BALANCE_ID, CREATED_AT, CREATED_BY, UPDATED_AT, EMPLOYEE_ID, LEAVE_TYPE, PENDING_DAYS,
    TOTAL_DAYS, USED_DAYS, YEAR, VERSION)
SELECT HRMS.LEAVE_BAL_SEQ.NEXTVAL, SYSTIMESTAMP, 'SEEDWF', SYSTIMESTAMP, x.EMPLOYEE_ID, x.CODE, 0, x.DAYS, 0, 2026, 0
  FROM (SELECT e.EMPLOYEE_ID, t.CODE, t.DAYS
          FROM HRMS.EMPLOYEES e
          JOIN HRMS.EMPLOYEE_JOB_DETAILS j ON j.EMPLOYEE_ID = e.EMPLOYEE_ID AND j.IS_CURRENT = 1
          CROSS JOIN (SELECT 'ANNUAL' CODE, 21 DAYS FROM DUAL UNION ALL SELECT 'CASUAL', 6 FROM DUAL
                      UNION ALL SELECT 'SICK', 15 FROM DUAL) t
         WHERE e.IS_ACTIVE = 1
           AND NOT EXISTS (SELECT 1 FROM HRMS.LEAVE_BALANCES b
                            WHERE b.EMPLOYEE_ID = e.EMPLOYEE_ID AND b.LEAVE_TYPE = t.CODE AND b.YEAR = 2026)) x;
-- Expect on dev: 867 rows (289 employees x 3; 0 on a second run).

UPDATE HRMS.AUTH_USERS
   SET PASSWORD_HASH = '$2a$10$0N43MRIC6Dx0E57CgyeOvOdwIbrw8bcgOo4Tj/QAnKuyjyUokWZoe',
       MUST_CHANGE_PASSWORD = 0, FAILED_ATTEMPTS = 0, IS_LOCKED = FALSE
 WHERE USERNAME IN ('anil.mehta', 'vikram.rathore') AND ROLE IN ('GENERAL_MANAGER', 'CEO');
-- Expect: 2 rows.

COMMIT;
