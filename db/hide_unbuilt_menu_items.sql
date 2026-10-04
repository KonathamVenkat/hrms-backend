-- Hides sidebar entries whose page does not exist in the Angular app yet.
--
-- Each of these SUB_MENU rows points at a route the frontend has no screen for, so clicking it
-- lands on the catch-all and sends the user to sign-in. The menu service skips rows with
-- IS_ACTIVE = 0, and a main menu left without visible children and without its own route
-- disappears from the sidebar. Nothing is deleted: set IS_ACTIVE back to 1 when the screen ships
-- (see the rollback section at the end).
--
-- Checked on 2026-10-04 against the routes in app.routes.ts and the feature routing files.
-- Pages that DO exist (and stay visible): Dashboard, Profile, Employees, Attendance (dashboard,
-- log, request, overtime, approval queue, OT approval), Leave (balances, requests, approval,
-- calendar), Payroll (components, structures, employee salary) and Admin Config.
--
-- Users who are signed in keep their cached menu until they sign out and in again.
-- To keep a module visible, delete its UPDATE (and its line in the rollback).

-- Employee: the pages below are not built (Profile and Employees stay).   Expect 8 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('EMP_DOCS', 'EMP_SHIFT_REQ', 'EMP_WORK_TYPE', 'EMP_ROT_SHIFT', 'EMP_ROT_WORK',
   'EMP_DISCPLN', 'EMP_POLICIES', 'EMP_ORG_CHART');

-- Recruitment: not built.                                                 Expect 4 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('REC_DASHBOARD', 'REC_JOBS', 'REC_CANDIDATES', 'REC_INTERVIEWS');

-- Attendance: Grace Time is not built.                                    Expect 1 row.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN ('ATT_GRACE');

-- Leave: dashboard, allocation, types (use Admin Config > Leave Types) and My Leave are not built.
--                                                                         Expect 4 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('LVE_DASHBOARD', 'LVE_ALLOCATION', 'LVE_TYPES', 'LVE_MY_LEAVE');

-- Payroll: dashboard, payslips, payroll run, allowances and deductions are not built.
--                                                                         Expect 5 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('PAY_DASHBOARD', 'PAY_PAYSLIPS', 'PAY_RUN', 'PAY_ALLOWANCES', 'PAY_DEDUCTIONS');

-- Performance: not built.                                                 Expect 4 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('PERF_DASHBOARD', 'PERF_KPI', 'PERF_FEEDBACK', 'PERF_REVIEW');

-- Offboarding: not built.                                                 Expect 3 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('OFF_RESIGN', 'OFF_FNF', 'OFF_CHECKLIST');

-- Asset: not built.                                                       Expect 3 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('AST_LIST', 'AST_MY_ASSETS', 'AST_REQUEST');

-- Help Desk: not built.                                                   Expect 3 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('HD_TICKETS', 'HD_MY_TICKETS', 'HD_FAQ');

-- Reporting: not built.                                                   Expect 4 rows.
UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 0 WHERE SUB_MENU_CODE IN
  ('RPT_HEADCOUNT', 'RPT_PAYROLL', 'RPT_LEAVE', 'RPT_ATTENDANCE');

-- Settings: a main menu with its own route (/app/settings) and no screen.  Expect 1 row.
UPDATE HRMS.MAIN_MENU SET IS_ACTIVE = 0 WHERE ROUTE = '/app/settings';

COMMIT;

-- Check: what each role still sees (should list only built pages).
-- SELECT ro.ROLE_NAME, m.MAIN_MENU_NAME, s.SUB_MENU_NAME, s.SUB_MENU_ACTION
--   FROM HRMS.ROLE_RIGHTS rr
--   JOIN HRMS.ROLES ro     ON ro.ROLE_ID = rr.ROLE_ID
--   JOIN HRMS.MAIN_MENU m  ON m.MAIN_MENU_ID = rr.MAIN_MENU_ID AND m.IS_ACTIVE = 1
--   LEFT JOIN HRMS.SUB_MENU s ON s.SUB_MENU_ID = rr.MENU_ID
--  WHERE s.SUB_MENU_ID IS NULL OR s.IS_ACTIVE = 1
--  ORDER BY ro.ROLE_ID, m.SORT_ORDER, s.SORT_ORDER;

-- Rollback (show everything again):
-- UPDATE HRMS.SUB_MENU SET IS_ACTIVE = 1 WHERE SUB_MENU_CODE IN
--   ('EMP_DOCS','EMP_SHIFT_REQ','EMP_WORK_TYPE','EMP_ROT_SHIFT','EMP_ROT_WORK','EMP_DISCPLN','EMP_POLICIES','EMP_ORG_CHART',
--    'REC_DASHBOARD','REC_JOBS','REC_CANDIDATES','REC_INTERVIEWS','ATT_GRACE',
--    'LVE_DASHBOARD','LVE_ALLOCATION','LVE_TYPES','LVE_MY_LEAVE',
--    'PAY_DASHBOARD','PAY_PAYSLIPS','PAY_RUN','PAY_ALLOWANCES','PAY_DEDUCTIONS',
--    'PERF_DASHBOARD','PERF_KPI','PERF_FEEDBACK','PERF_REVIEW','OFF_RESIGN','OFF_FNF','OFF_CHECKLIST',
--    'AST_LIST','AST_MY_ASSETS','AST_REQUEST','HD_TICKETS','HD_MY_TICKETS','HD_FAQ',
--    'RPT_HEADCOUNT','RPT_PAYROLL','RPT_LEAVE','RPT_ATTENDANCE');
-- UPDATE HRMS.MAIN_MENU SET IS_ACTIVE = 1 WHERE ROUTE = '/app/settings';
-- COMMIT;
