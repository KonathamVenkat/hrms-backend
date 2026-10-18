-- Admin Config had grown to 12 entries and scrolled. This splits the master-data screens into topic menus:
--   Organisation        Companies, Company Branches, Departments, Designations
--   Banking             Banks, Bank Branches
--   Locations           Office Locations
--   Employment Masters  Document Types
--   Time and Leave        Work Shifts, Holiday Calendar, Leave Types
--   Admin Config        Audit Trail (stays, now last in the rail)
-- Personal Masters (religions, nationalities, languages, language levels, house types, relations) is not created
-- yet: a menu with no screens would show an empty flyout. Add it, and put each new master in its group, when its
-- screen is built (same pattern as below).
--
-- Data only; the URLs do not change. The sidebar shows a main menu to a role once that role has any right under it,
-- so moving a sub-menu means moving its SUB_MENU row and its ROLE_RIGHTS rows. Nobody gains or loses access.
-- Run AFTER db/pneed_masters_menu.sql (it creates the Banks / Companies rows this moves). Safe to run twice.
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches it).

-- 1. The new main menus (no route of their own: they only open a flyout, like Employee).
INSERT INTO HRMS.MAIN_MENU (MAIN_MENU_NAME, ICON, SORT_ORDER, ROUTE, IS_ACTIVE)
SELECT v.nm, v.icon, v.so, NULL, 1
  FROM (SELECT 'Organisation' nm, 'corporate_fare' icon, 13 so FROM DUAL
        UNION ALL SELECT 'Banking', 'account_balance', 14 FROM DUAL
        UNION ALL SELECT 'Locations', 'location_on', 15 FROM DUAL
        UNION ALL SELECT 'Employment Masters', 'work', 16 FROM DUAL
        UNION ALL SELECT 'Time and Leave', 'schedule', 17 FROM DUAL) v
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.MAIN_MENU m WHERE m.MAIN_MENU_NAME = v.nm);
-- Expect: 5 rows inserted (0 on a second run).

-- 2. Move the rights first (they are matched through the sub-menu), then the sub-menus themselves.
MERGE INTO HRMS.ROLE_RIGHTS rr
USING (SELECT s.SUB_MENU_ID, mm.MAIN_MENU_ID
         FROM (SELECT 'ADMIN_COMPANIES' code, 'Organisation' grp FROM DUAL
               UNION ALL SELECT 'ADMIN_COMPANY_BRANCHES', 'Organisation' FROM DUAL
               UNION ALL SELECT 'ADMIN_DEPARTMENTS', 'Organisation' FROM DUAL
               UNION ALL SELECT 'ADMIN_DESIGNATIONS', 'Organisation' FROM DUAL
               UNION ALL SELECT 'ADMIN_BANKS', 'Banking' FROM DUAL
               UNION ALL SELECT 'ADMIN_BANK_BRANCHES', 'Banking' FROM DUAL
               UNION ALL SELECT 'ADMIN_LOCATIONS', 'Locations' FROM DUAL
               UNION ALL SELECT 'ADMIN_DOC_TYPES', 'Employment Masters' FROM DUAL
               UNION ALL SELECT 'ADMIN_WORK_SHIFTS', 'Time and Leave' FROM DUAL
               UNION ALL SELECT 'ADMIN_HOLIDAYS', 'Time and Leave' FROM DUAL
               UNION ALL SELECT 'ADMIN_LEAVE_TYPES', 'Time and Leave' FROM DUAL) v
         JOIN HRMS.SUB_MENU s ON s.SUB_MENU_CODE = v.code
         JOIN HRMS.MAIN_MENU mm ON mm.MAIN_MENU_NAME = v.grp) x
   ON (rr.MENU_ID = x.SUB_MENU_ID)
 WHEN MATCHED THEN UPDATE SET rr.MAIN_MENU_ID = x.MAIN_MENU_ID;
-- Expect: one row per role right of these 11 screens (22 on dev with both roles, 0 changes on a second run).

MERGE INTO HRMS.SUB_MENU s
USING (SELECT v.code, v.so, mm.MAIN_MENU_ID
         FROM (SELECT 'ADMIN_COMPANIES' code, 'Organisation' grp, 1 so FROM DUAL
               UNION ALL SELECT 'ADMIN_COMPANY_BRANCHES', 'Organisation', 2 FROM DUAL
               UNION ALL SELECT 'ADMIN_DEPARTMENTS', 'Organisation', 3 FROM DUAL
               UNION ALL SELECT 'ADMIN_DESIGNATIONS', 'Organisation', 4 FROM DUAL
               UNION ALL SELECT 'ADMIN_BANKS', 'Banking', 1 FROM DUAL
               UNION ALL SELECT 'ADMIN_BANK_BRANCHES', 'Banking', 2 FROM DUAL
               UNION ALL SELECT 'ADMIN_LOCATIONS', 'Locations', 1 FROM DUAL
               UNION ALL SELECT 'ADMIN_DOC_TYPES', 'Employment Masters', 1 FROM DUAL
               UNION ALL SELECT 'ADMIN_WORK_SHIFTS', 'Time and Leave', 1 FROM DUAL
               UNION ALL SELECT 'ADMIN_HOLIDAYS', 'Time and Leave', 2 FROM DUAL
               UNION ALL SELECT 'ADMIN_LEAVE_TYPES', 'Time and Leave', 3 FROM DUAL) v
         JOIN HRMS.MAIN_MENU mm ON mm.MAIN_MENU_NAME = v.grp) x
   ON (s.SUB_MENU_CODE = x.code)
 WHEN MATCHED THEN UPDATE SET s.MAIN_MENU_ID = x.MAIN_MENU_ID, s.SORT_ORDER = x.so;
-- Expect: 11 rows merged.

-- 3. Admin Config keeps only Audit Trail, so it moves to the end of the rail, after the topic menus.
UPDATE HRMS.MAIN_MENU SET SORT_ORDER = 18 WHERE MAIN_MENU_NAME = 'Admin Config';
-- Expect: 1 row.

COMMIT;

-- To undo (puts everything back under Admin Config in its old order):
-- UPDATE HRMS.ROLE_RIGHTS SET MAIN_MENU_ID = (SELECT MAIN_MENU_ID FROM HRMS.MAIN_MENU WHERE MAIN_MENU_NAME = 'Admin Config')
--  WHERE MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE IN
--   ('ADMIN_COMPANIES','ADMIN_COMPANY_BRANCHES','ADMIN_DEPARTMENTS','ADMIN_DESIGNATIONS','ADMIN_BANKS','ADMIN_BANK_BRANCHES',
--    'ADMIN_LOCATIONS','ADMIN_DOC_TYPES','ADMIN_WORK_SHIFTS','ADMIN_HOLIDAYS','ADMIN_LEAVE_TYPES'));
-- UPDATE HRMS.SUB_MENU SET MAIN_MENU_ID = (SELECT MAIN_MENU_ID FROM HRMS.MAIN_MENU WHERE MAIN_MENU_NAME = 'Admin Config'),
--        SORT_ORDER = CASE SUB_MENU_CODE WHEN 'ADMIN_LEAVE_TYPES' THEN 1 WHEN 'ADMIN_HOLIDAYS' THEN 2 WHEN 'ADMIN_WORK_SHIFTS' THEN 3
--          WHEN 'ADMIN_LOCATIONS' THEN 4 WHEN 'ADMIN_DOC_TYPES' THEN 5 WHEN 'ADMIN_DEPARTMENTS' THEN 7 WHEN 'ADMIN_DESIGNATIONS' THEN 8
--          WHEN 'ADMIN_BANKS' THEN 9 WHEN 'ADMIN_BANK_BRANCHES' THEN 10 WHEN 'ADMIN_COMPANIES' THEN 11 ELSE 12 END
--  WHERE SUB_MENU_CODE IN ('ADMIN_COMPANIES','ADMIN_COMPANY_BRANCHES','ADMIN_DEPARTMENTS','ADMIN_DESIGNATIONS','ADMIN_BANKS',
--    'ADMIN_BANK_BRANCHES','ADMIN_LOCATIONS','ADMIN_DOC_TYPES','ADMIN_WORK_SHIFTS','ADMIN_HOLIDAYS','ADMIN_LEAVE_TYPES');
-- UPDATE HRMS.MAIN_MENU SET SORT_ORDER = 9 WHERE MAIN_MENU_NAME = 'Admin Config';
-- DELETE FROM HRMS.MAIN_MENU WHERE MAIN_MENU_NAME IN ('Organisation','Banking','Locations','Employment Masters','Time and Leave');
-- COMMIT;
