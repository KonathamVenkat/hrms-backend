-- SYSTEM_ADMIN: role rights, Admin Config > Departments menu, and the first SYSTEM_ADMIN account.
--
-- Run AFTER the backend with UserRole.SYSTEM_ADMIN (common-lib) is deployed: an older backend cannot
-- read an AUTH_USERS row whose ROLE it does not know. The sidebar link does nothing until the Angular
-- Departments screen exists; the API (/api/v1/admin/departments) works as soon as the backend is up.
--
-- Data only, no schema change. Safe to run twice: every INSERT skips a row that already exists.
-- The ROLES row for SYSTEM_ADMIN and the CK_AUTH_USERS_ROLE constraint already allow this role.
--
-- The sysadmin account is created with MUST_CHANGE_PASSWORD = 1. Before running, replace
-- <BCRYPT_HASH_OF_TEMP_PASSWORD> in step 5 with a BCrypt hash (cost 10, $2a$) of a one-time password
-- that meets the password policy. Never commit a real hash: this repository is public.
-- The user must change the password at first sign-in.
--
-- Signed-in users keep their old menu until they sign out and in again (the sidebar caches it).

-- 1. The role (already present on dev; here for a database that lacks it).
INSERT INTO HRMS.ROLES (ROLE_NAME, DESCRIPTION)
SELECT 'SYSTEM_ADMIN', 'Manage system configuration and user access'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.ROLES WHERE ROLE_NAME = 'SYSTEM_ADMIN');
-- Expect: 0 rows on dev (role id 6 exists), 1 on a database without it.

-- 2. Admin Config > Departments.
INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT 'ADMIN_DEPARTMENTS', 'Departments', '/app/admin/departments', 1, 112, 7, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_DEPARTMENTS');
-- Expect: 1 row inserted (0 on a second run).

-- 3. SYSTEM_ADMIN gets exactly the rights HR_ADMIN has today. This runs BEFORE step 4, so the
--    Departments row below is not copied to anyone else.
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT sa.ROLE_ID, rr.MAIN_MENU_ID, rr.MENU_ID, rr.CAN_VIEW, rr.CAN_CREATE, rr.CAN_EDIT, rr.CAN_DELETE
  FROM HRMS.ROLE_RIGHTS rr
  JOIN HRMS.ROLES ha ON ha.ROLE_ID = rr.ROLE_ID AND ha.ROLE_NAME = 'HR_ADMIN'
  JOIN HRMS.ROLES sa ON sa.ROLE_NAME = 'SYSTEM_ADMIN'
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x
                    WHERE x.ROLE_ID = sa.ROLE_ID
                      AND x.MAIN_MENU_ID = rr.MAIN_MENU_ID
                      AND NVL(x.MENU_ID, -1) = NVL(rr.MENU_ID, -1));
-- Expect: one row per HR_ADMIN right (62 on dev, before Departments), 0 on a second run.

-- 4. Departments: SYSTEM_ADMIN only, full rights (HR_ADMIN does not get this menu).
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, 112, s.SUB_MENU_ID, 1, 1, 1, 1
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'SYSTEM_ADMIN'
   AND s.SUB_MENU_CODE = 'ADMIN_DEPARTMENTS'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 1 row inserted (0 on a second run).

-- 4b. Let AUTH_USERS.ROLE hold SYSTEM_ADMIN. Dev already has CK_AUTH_USERS_ROLE (10 roles); a database
--     built from V1__baseline.sql still has the old unnamed 3-role check, which would reject step 5.
DECLARE
  v_exists NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_exists FROM ALL_CONSTRAINTS
   WHERE OWNER = 'HRMS' AND TABLE_NAME = 'AUTH_USERS' AND CONSTRAINT_NAME = 'CK_AUTH_USERS_ROLE';
  IF v_exists = 0 THEN
    FOR c IN (SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS
               WHERE OWNER = 'HRMS' AND TABLE_NAME = 'AUTH_USERS' AND CONSTRAINT_TYPE = 'C'
                 AND UPPER(SEARCH_CONDITION_VC) LIKE '%ROLE%IN%(%') LOOP
      EXECUTE IMMEDIATE 'ALTER TABLE HRMS.AUTH_USERS DROP CONSTRAINT "' || c.CONSTRAINT_NAME || '"';
    END LOOP;
    EXECUTE IMMEDIATE q'[ALTER TABLE HRMS.AUTH_USERS ADD CONSTRAINT CK_AUTH_USERS_ROLE CHECK ("ROLE" IN
      ('SYSTEM_ADMIN','HR_ADMIN','HR_MANAGER','EMPLOYEE','REPORTING_MANAGER',
       'ATTENDANCE_OFFICER','PAYROLL_OFFICER','PAYROLL_APPROVER','FINANCE_USER','AUDITOR'))]';
  END IF;
END;
/
-- Expect: "PL/SQL procedure successfully completed." (no change on dev, which already has the constraint).

-- 5. The first SYSTEM_ADMIN account (no employee record: EMPLOYEE_ID stays NULL).
INSERT INTO HRMS.AUTH_USERS (USER_ID, USERNAME, EMAIL, PASSWORD_HASH, ROLE, FULL_NAME_EN,
                             IS_ACTIVE, IS_LOCKED, FAILED_ATTEMPTS, MUST_CHANGE_PASSWORD,
                             CREATED_BY, CREATED_AT)
SELECT HRMS.AUTH_USER_SEQ.NEXTVAL, 'sysadmin', 'sysadmin@hrms.com',
       '<BCRYPT_HASH_OF_TEMP_PASSWORD>',
       'SYSTEM_ADMIN', 'System Administrator',
       TRUE, FALSE, 0, 1,
       'SYSTEM', SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.AUTH_USERS WHERE USERNAME = 'sysadmin' OR EMAIL = 'sysadmin@hrms.com');
-- Expect: 1 row inserted (0 on a second run).

COMMIT;

-- Check afterwards:
-- SELECT COUNT(*) FROM HRMS.ROLE_RIGHTS WHERE ROLE_ID = (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'SYSTEM_ADMIN');   -- HR_ADMIN count + 1
-- SELECT USERNAME, ROLE, MUST_CHANGE_PASSWORD FROM HRMS.AUTH_USERS WHERE USERNAME = 'sysadmin';

-- To undo (the role constraint is left as is: it only allows more roles, it blocks nothing):
-- DELETE FROM HRMS.AUTH_USERS WHERE USERNAME = 'sysadmin';
-- DELETE FROM HRMS.ROLE_RIGHTS WHERE ROLE_ID = (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'SYSTEM_ADMIN');
-- DELETE FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_DEPARTMENTS';
-- COMMIT;
