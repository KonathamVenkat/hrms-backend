-- Admin Config > Banks, Bank Branches, Companies, Company Branches, for SYSTEM_ADMIN (full rights).
-- The screens are at /app/admin/banks, /bank-branches, /companies, /company-branches and use
-- /api/v1/admin/<same names> (SYSTEM_ADMIN only). Run AFTER V5 and after the new backend and frontend
-- builds are deployed: the links do nothing before that.
-- Data only. Safe to run twice: each INSERT skips a row that exists.
-- Users who are signed in keep the old menu until they sign out and in again.

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT v.code, v.nm, v.act, 1, 112, v.so, 1
  FROM (SELECT 'ADMIN_BANKS' code, 'Banks' nm, '/app/admin/banks' act, 9 so FROM DUAL
        UNION ALL SELECT 'ADMIN_BANK_BRANCHES', 'Bank Branches', '/app/admin/bank-branches', 10 FROM DUAL
        UNION ALL SELECT 'ADMIN_COMPANIES', 'Companies', '/app/admin/companies', 11 FROM DUAL
        UNION ALL SELECT 'ADMIN_COMPANY_BRANCHES', 'Company Branches', '/app/admin/company-branches', 12 FROM DUAL) v
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = v.code);
-- Expect: 4 rows inserted (0 on a second run).

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, 112, s.SUB_MENU_ID, 1, 1, 1, 1
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'SYSTEM_ADMIN'
   AND s.SUB_MENU_CODE IN ('ADMIN_BANKS', 'ADMIN_BANK_BRANCHES', 'ADMIN_COMPANIES', 'ADMIN_COMPANY_BRANCHES')
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 4 rows inserted (0 on a second run).

COMMIT;

-- To undo:
-- DELETE FROM HRMS.ROLE_RIGHTS WHERE MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE IN ('ADMIN_BANKS','ADMIN_BANK_BRANCHES','ADMIN_COMPANIES','ADMIN_COMPANY_BRANCHES'));
-- DELETE FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE IN ('ADMIN_BANKS','ADMIN_BANK_BRANCHES','ADMIN_COMPANIES','ADMIN_COMPANY_BRANCHES');
-- COMMIT;
