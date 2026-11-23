-- Leave Management > Leave Request (/app/leave/requests) for GENERAL_MANAGER, like EMPLOYEE and CEO already have.
-- The API already lets a General Manager apply for and see their own leave (role hierarchy GENERAL_MANAGER > EMPLOYEE);
-- only the sidebar entry was missing. Their own request is decided by the CEO at the General Manager step.
-- Data only. Safe to run twice. Sign out and in again to see it. Undo: db/general_manager_leave_menu_rollback.sql
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 0, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'GENERAL_MANAGER'
   AND s.SUB_MENU_CODE = 'LEAVE_REQUEST'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 1 row inserted (0 on a second run).
COMMIT;
