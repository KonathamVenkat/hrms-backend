-- Sidebar entry Employment Masters > Qualification Levels (/app/admin/qualification-levels) for SYSTEM_ADMIN, who
-- maintains the masters (like Grades). HR_ADMIN does not get it: HR reads the levels in the Assign Salary dropdown.
-- Run as the HRMS user, after V32. Data only. Safe to run twice: each INSERT skips a row that exists.
-- Undo: db/qualification_levels_menu_rollback.sql

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT 'ADMIN_QUALIFICATION_LEVELS', 'Qualification Levels', '/app/admin/qualification-levels', 1, mm.MAIN_MENU_ID, 6, 1
  FROM HRMS.MAIN_MENU mm
 WHERE mm.MAIN_MENU_NAME = 'Employment Masters'
   AND NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = 'ADMIN_QUALIFICATION_LEVELS');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 1, 1
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'SYSTEM_ADMIN'
   AND s.SUB_MENU_CODE = 'ADMIN_QUALIFICATION_LEVELS'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 1 row inserted (0 on a second run). Sign out and in again to see it.

COMMIT;
