-- Admin Config > Designations in the sidebar, for SYSTEM_ADMIN only.
-- The screen is at /app/admin/designations and uses /api/v1/admin/designations (SYSTEM_ADMIN only).
-- Data only: run after db/system_admin_and_departments.sql (the SYSTEM_ADMIN role must exist), and deploy
-- the backend first so the API answers. Safe to run twice: each INSERT skips a row that exists.
--
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches it).

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT 'ADMIN_DESIGNATIONS', 'Designations', '/app/admin/designations', 1, 112, 8, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_DESIGNATIONS');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, 112, s.SUB_MENU_ID, 1, 1, 1, 1
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'SYSTEM_ADMIN'
   AND s.SUB_MENU_CODE = 'ADMIN_DESIGNATIONS'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 1 row inserted (0 on a second run).

COMMIT;

-- Check afterwards (expect 64 rights for SYSTEM_ADMIN: 63 before + this one):
-- SELECT COUNT(*) FROM HRMS.ROLE_RIGHTS WHERE ROLE_ID = (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'SYSTEM_ADMIN');

-- To undo:
-- DELETE FROM HRMS.ROLE_RIGHTS WHERE MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_DESIGNATIONS');
-- DELETE FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_DESIGNATIONS';
-- COMMIT;
