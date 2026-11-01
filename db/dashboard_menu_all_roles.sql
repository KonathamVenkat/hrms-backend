-- The Dashboard sidebar icon (MAIN_MENU 100, route /app/dashboard) only had a right row for EMPLOYEE and HR_MANAGER, so
-- HR_ADMIN, SYSTEM_ADMIN and the other roles saw no way back to the dashboard. This gives every role view access to it.
-- Run by hand as HRMS. Safe to run twice. Users must sign in again (the menu is cached per sign-in).
-- Undo: db/dashboard_menu_all_roles_rollback.sql
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, 100, NULL, 1, 0, 0, 0
  FROM HRMS.ROLES r
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x
                    WHERE x.ROLE_ID = r.ROLE_ID AND x.MAIN_MENU_ID = 100 AND x.MENU_ID IS NULL);
-- Expect: one row per role that had none (0 on a second run).
COMMIT;
