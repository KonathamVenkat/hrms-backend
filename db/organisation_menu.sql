-- Organisation menu (workflow phase 1): Organisation > Branch Setup and Branch Assignments (HR_ADMIN, HR_MANAGER).
-- The screens are at /app/organisation/branches and /app/organisation/branch-assignments and use
-- /api/v1/organisation/*. Run AFTER V8 and after the new backend and frontend builds are deployed:
-- the links do nothing before that.
-- SYSTEM_ADMIN keeps maintaining branch code, name and company in Admin Config > Company Branches; HR sets each
-- branch's location and manager here, and assigns every employee a branch.
-- The sidebar shows a main menu to a role once that role has any right under it, so no main-menu row is needed.
-- Data only. Safe to run twice: each INSERT skips a row that exists.
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches it).

INSERT INTO HRMS.MAIN_MENU (MAIN_MENU_NAME, ICON, SORT_ORDER, ROUTE, IS_ACTIVE)
SELECT 'Organisation', 'account_tree', 19, NULL, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.MAIN_MENU m WHERE m.MAIN_MENU_NAME = 'Organisation');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT v.code, v.nm, v.act, 1, mm.MAIN_MENU_ID, v.so, 1
  FROM (SELECT 'ORG_BRANCH_SETUP' code, 'Branch Setup' nm, '/app/organisation/branches' act, 1 so FROM DUAL
        UNION ALL SELECT 'ORG_BRANCH_ASSIGNMENTS', 'Branch Assignments', '/app/organisation/branch-assignments', 2 FROM DUAL) v
  JOIN HRMS.MAIN_MENU mm ON mm.MAIN_MENU_NAME = 'Organisation'
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = v.code);
-- Expect: 2 rows inserted (0 on a second run).

-- HR_ADMIN and HR_MANAGER: both screens. Nothing here is deleted from the screens, so no delete right.
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 1, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME IN ('HR_ADMIN', 'HR_MANAGER')
   AND s.SUB_MENU_CODE IN ('ORG_BRANCH_SETUP', 'ORG_BRANCH_ASSIGNMENTS')
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 4 rows inserted (0 on a second run).

COMMIT;
