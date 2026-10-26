-- The General Manager login: gives an existing employee the GENERAL_MANAGER role (login account).
--
-- Run AFTER V10 and after a backend with UserRole.GENERAL_MANAGER (common-lib reinstalled) is deployed: an older
-- backend cannot read an AUTH_USERS row whose ROLE it does not know. Data only, no schema change.
--
-- Before running, replace the placeholders below:
--   <EMPLOYEE_ID>   the EMPLOYEES.EMPLOYEE_ID of the General Manager (the person must already exist as an employee)
--   <USERNAME>      the sign-in name, 3-50 characters (used only if the employee has no login yet)
--   <BCRYPT_HASH_OF_TEMP_PASSWORD>  a BCrypt hash (cost 10, $2a$) of a one-time password that meets the password
--                   policy; create it the way the sysadmin script does (a BCrypt generator of your choice, or the
--                   backend's PasswordEncoder). Never commit a real hash or password: this repository is public.
-- The account is created with MUST_CHANGE_PASSWORD = 1; the user changes the password at first sign-in.
--
-- If the employee already has an EMPLOYEE login (created with the employee record), step 1 only changes its ROLE
-- and the password is left alone. A login with an HR or admin role is not changed (see step 1). The user must sign out and in again; old tokens are refused after the role change.
-- Safe to run twice.

-- 1. Existing plain employee login: switch it to GENERAL_MANAGER.
--    Only a login whose role is EMPLOYEE is changed. A login with any other role (HR_ADMIN, HR_MANAGER,
--    SYSTEM_ADMIN, ...) is left alone on purpose: one login has one role, so making it GENERAL_MANAGER would take
--    away the HR or admin rights. Decide that case by hand (for example a separate login for the GM duties).
UPDATE HRMS.AUTH_USERS
   SET ROLE = 'GENERAL_MANAGER', UPDATED_BY = 'SYSTEM', UPDATED_AT = SYSTIMESTAMP
 WHERE EMPLOYEE_ID = <EMPLOYEE_ID>
   AND ROLE = 'EMPLOYEE';
-- Expect: 1 row if the employee had an EMPLOYEE login, else 0 (no login, already GENERAL_MANAGER, or another role:
-- check with the query at the bottom).

-- 2. No login yet: create one linked to the employee. The e-mail is the work e-mail, else the personal one; an
--    employee with neither fails here (EMAIL is required): add an e-mail to the employee record first.
INSERT INTO HRMS.AUTH_USERS (USER_ID, USERNAME, EMAIL, PASSWORD_HASH, ROLE, EMPLOYEE_ID, EMPLOYEE_CODE, FULL_NAME_EN,
                             IS_ACTIVE, IS_LOCKED, FAILED_ATTEMPTS, MUST_CHANGE_PASSWORD, CREATED_BY, CREATED_AT)
SELECT HRMS.AUTH_USER_SEQ.NEXTVAL, '<USERNAME>', NVL(e.WORK_EMAIL, e.PERSONAL_EMAIL), '<BCRYPT_HASH_OF_TEMP_PASSWORD>',
       'GENERAL_MANAGER', e.EMPLOYEE_ID, e.EMPLOYEE_CODE,
       TRIM(e.FIRST_NAME || ' ' || e.LAST_NAME),
       TRUE, FALSE, 0, 1, 'SYSTEM', SYSTIMESTAMP
  FROM HRMS.EMPLOYEES e
 WHERE e.EMPLOYEE_ID = <EMPLOYEE_ID>
   AND NOT EXISTS (SELECT 1 FROM HRMS.AUTH_USERS a WHERE a.EMPLOYEE_ID = e.EMPLOYEE_ID);
-- Expect: 1 row if the employee had no login, else 0.

COMMIT;

-- Check afterwards:
-- SELECT USERNAME, ROLE, EMPLOYEE_ID, MUST_CHANGE_PASSWORD FROM HRMS.AUTH_USERS WHERE EMPLOYEE_ID = <EMPLOYEE_ID>;

-- To undo (back to a normal employee login):
-- UPDATE HRMS.AUTH_USERS SET ROLE = 'EMPLOYEE' WHERE EMPLOYEE_ID = <EMPLOYEE_ID> AND ROLE = 'GENERAL_MANAGER';
-- COMMIT;
