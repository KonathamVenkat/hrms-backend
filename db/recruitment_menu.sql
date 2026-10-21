-- Recruitment menu (phase R1): Recruitment > Requisitions (HR_ADMIN, HR_MANAGER, EMPLOYEE),
-- Recruitment > Pipeline Stages and Rejection Reasons (SYSTEM_ADMIN).
-- The screens are at /app/recruitment/requisitions, /stages and /rejection-reasons and use
-- /api/v1/recruitment/* and /api/v1/admin/recruitment/*. Run AFTER V7 and after the new backend and frontend
-- builds are deployed: the links do nothing before that.
-- EMPLOYEE gets the Requisitions entry because a department head is an ordinary employee; the API still shows
-- each person only what they raised or what belongs to a department they head, and only HR can approve.
-- The sidebar shows a main menu to a role once that role has any right under it, so no main-menu row is needed.
-- Data only. Safe to run twice: each INSERT skips a row that exists.
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches it).

INSERT INTO HRMS.MAIN_MENU (MAIN_MENU_NAME, ICON, SORT_ORDER, ROUTE, IS_ACTIVE)
SELECT 'Recruitment', 'person_search', 18, NULL, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.MAIN_MENU m WHERE m.MAIN_MENU_NAME = 'Recruitment');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT v.code, v.nm, v.act, 1, mm.MAIN_MENU_ID, v.so, 1
  FROM (SELECT 'REC_REQUISITIONS' code, 'Requisitions' nm, '/app/recruitment/requisitions' act, 1 so FROM DUAL
        UNION ALL SELECT 'REC_STAGES', 'Pipeline Stages', '/app/recruitment/stages', 2 FROM DUAL
        UNION ALL SELECT 'REC_REJECTION_REASONS', 'Rejection Reasons', '/app/recruitment/rejection-reasons', 3 FROM DUAL) v
  JOIN HRMS.MAIN_MENU mm ON mm.MAIN_MENU_NAME = 'Recruitment'
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = v.code);
-- Expect: 3 rows inserted (0 on a second run).

-- HR_ADMIN, HR_MANAGER and EMPLOYEE: Requisitions. Nobody deletes a requisition; they cancel it.
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 1, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME IN ('HR_ADMIN', 'HR_MANAGER', 'EMPLOYEE')
   AND s.SUB_MENU_CODE = 'REC_REQUISITIONS'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 3 rows inserted (0 on a second run).

-- SYSTEM_ADMIN: the two set-up screens, full rights.
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 1, 1
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'SYSTEM_ADMIN'
   AND s.SUB_MENU_CODE IN ('REC_STAGES', 'REC_REJECTION_REASONS')
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 2 rows inserted (0 on a second run).

COMMIT;

-- To undo:
-- DELETE FROM HRMS.ROLE_RIGHTS WHERE MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE IN ('REC_REQUISITIONS','REC_STAGES','REC_REJECTION_REASONS'));
-- DELETE FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE IN ('REC_REQUISITIONS','REC_STAGES','REC_REJECTION_REASONS');
-- DELETE FROM HRMS.MAIN_MENU WHERE MAIN_MENU_NAME = 'Recruitment';
-- COMMIT;
