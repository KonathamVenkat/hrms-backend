-- Admin Config > Audit Trail in the sidebar, for HR_ADMIN only.
-- The screen is at /app/admin/audit-events and reads GET /api/v1/admin/audit-events (HR_ADMIN only).
-- Data only: no backend or frontend deploy order applies, but the link does nothing until the new
-- frontend build is deployed.
--
-- SUB_MENU_ID and ROLE_RIGHTS_ID come from their sequences. Only HR_ADMIN gets a row, and with
-- view only (the trail is read-only). Safe to run twice: each INSERT skips a row that exists.
--
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches
-- it for the session).

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT 'ADMIN_AUDIT', 'Audit Trail', '/app/admin/audit-events', 1, 112, 6, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_AUDIT');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, 112, s.SUB_MENU_ID, 1, 0, 0, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'HR_ADMIN'
   AND s.SUB_MENU_CODE = 'ADMIN_AUDIT'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 1 row inserted (0 on a second run).

COMMIT;

-- To undo:
-- DELETE FROM HRMS.ROLE_RIGHTS WHERE MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_AUDIT');
-- DELETE FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_AUDIT';
-- COMMIT;
