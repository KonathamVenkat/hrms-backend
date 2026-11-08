-- UNDO of db/system_admin_only_masters_menu.sql: gives the HR_ADMIN role its five sidebar entries back (view only).
-- The backend would still refuse HR_ADMIN on those admin endpoints, so only run this together with undoing that change.
-- Run as the HRMS user. Safe to run twice.
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, sm.MAIN_MENU_ID, sm.SUB_MENU_ID, 1, 0, 0, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU sm
 WHERE r.ROLE_NAME = 'HR_ADMIN'
   AND sm.SUB_MENU_ACTION IN ('/app/admin/document-types', '/app/admin/office-locations', '/app/admin/work-shifts',
                              '/app/admin/leave-types', '/app/admin/holiday-calendar')
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = sm.SUB_MENU_ID);

COMMIT;
