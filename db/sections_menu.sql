-- Adds the Sections master screen to the sidebar under Organisation, for SYSTEM_ADMIN only (like Directorates).
-- Run by hand as HRMS after V14. Safe to run twice. Undo: db/sections_menu_rollback.sql
INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT 'ADMIN_SECTIONS', 'Sections', '/app/admin/sections', 1, mm.MAIN_MENU_ID, 6, 1
  FROM HRMS.MAIN_MENU mm
 WHERE mm.MAIN_MENU_NAME = 'Organisation'
   AND NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = 'ADMIN_SECTIONS');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 1, 1
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'SYSTEM_ADMIN'
   AND s.SUB_MENU_CODE = 'ADMIN_SECTIONS'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 1 row inserted (0 on a second run).

COMMIT;
