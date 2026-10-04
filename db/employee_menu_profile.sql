-- Employee > Profile in the sidebar opens the signed-in user's own profile screen.
-- The SUB_MENU row EMP_PROFILE (shared by HR_ADMIN, HR_MANAGER and EMPLOYEE) pointed at
-- /app/employee/profile, a page that does not exist; the screen lives at /app/profile and is open
-- to every role. No backend or frontend deploy order applies: this is data only.
--
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches
-- it for the session).

UPDATE HRMS.SUB_MENU
   SET SUB_MENU_ACTION = '/app/profile'
 WHERE SUB_MENU_CODE   = 'EMP_PROFILE'
   AND SUB_MENU_ACTION = '/app/employee/profile';

-- Expect: 1 row updated.
COMMIT;

-- To undo:
-- UPDATE HRMS.SUB_MENU SET SUB_MENU_ACTION = '/app/employee/profile' WHERE SUB_MENU_CODE = 'EMP_PROFILE';
-- COMMIT;
