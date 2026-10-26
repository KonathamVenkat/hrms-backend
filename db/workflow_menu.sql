-- Approvals menu (approval workflow): Approvals > My Approvals (/app/workflow/inbox) for every active role.
-- (recruitment_menu.sql and audit_trail_menu.sql list roles because those screens show role-specific data;
-- the inbox is filtered by the server per user, so every login role gets the link, as the task brief asks.)
-- Run AFTER V10 and after the new backend and frontend builds are deployed: the link does nothing before that.
-- Every signed-in user may open the inbox; the server decides what it contains (a branch manager, department
-- secretary or head is a relationship, not a role, so the screen is shown to EMPLOYEE too).
-- Data only. Safe to run twice: each INSERT skips a row that exists.
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches it).

INSERT INTO HRMS.MAIN_MENU (MAIN_MENU_NAME, ICON, SORT_ORDER, ROUTE, IS_ACTIVE)
SELECT 'Approvals', 'fact_check', 20, NULL, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.MAIN_MENU m WHERE m.MAIN_MENU_NAME = 'Approvals');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT 'WORKFLOW_INBOX', 'My Approvals', '/app/workflow/inbox', 1, mm.MAIN_MENU_ID, 1, 1
  FROM HRMS.MAIN_MENU mm
 WHERE mm.MAIN_MENU_NAME = 'Approvals'
   AND NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = 'WORKFLOW_INBOX');
-- Expect: 1 row inserted (0 on a second run).

-- View only: acting is checked by the server per request, not by menu rights.
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 0, 0, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.IS_ACTIVE = 1
   AND s.SUB_MENU_CODE = 'WORKFLOW_INBOX'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: one row per active role in HRMS.ROLES (11 once V10 has run), 0 on a second run.

COMMIT;

-- ===== ROLLBACK (run by hand to undo this script) =====
-- DELETE FROM HRMS.ROLE_RIGHTS WHERE MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'WORKFLOW_INBOX');
-- DELETE FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'WORKFLOW_INBOX';
-- DELETE FROM HRMS.MAIN_MENU WHERE MAIN_MENU_NAME = 'Approvals';
-- COMMIT;
