-- Sidebar entries for the 16 new master screens, for SYSTEM_ADMIN (full rights).
-- The screens are at /app/admin/<name> and use /api/v1/admin/<same name> (SYSTEM_ADMIN only). Run AFTER the new
-- backend and frontend builds are deployed: the links do nothing before that. Needs db/masters_menu_groups.sql
-- (it creates the Locations, Organisation and Employment Masters menus) to have been run.
--   Locations           (existing)  + Regions, Wilayas, Provinces, Cities
--   Organisation        (existing)  + Directorates
--   Employment Masters  (existing)  + Grades, Contract Types, Professions, Education Levels
--   Personal Masters    (NEW menu)  Nationalities, Religions, Languages, Language Levels, House Types,
--                                   Relation Types, Relations
-- Time and Leave moves one place down and Admin Config to the end so the new menu sits before them.
-- Data only. Safe to run twice: each INSERT skips a row that exists.
-- Users who are signed in keep the old menu until they sign out and in again (the sidebar caches it).

INSERT INTO HRMS.MAIN_MENU (MAIN_MENU_NAME, ICON, SORT_ORDER, ROUTE, IS_ACTIVE)
SELECT 'Personal Masters', 'person', 17, NULL, 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.MAIN_MENU m WHERE m.MAIN_MENU_NAME = 'Personal Masters');
-- Expect: 1 row inserted (0 on a second run).

UPDATE HRMS.MAIN_MENU SET SORT_ORDER = 18 WHERE MAIN_MENU_NAME = 'Time and Leave';
UPDATE HRMS.MAIN_MENU SET SORT_ORDER = 19 WHERE MAIN_MENU_NAME = 'Admin Config';
-- Expect: 1 row each.

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT v.code, v.nm, v.act, 1, mm.MAIN_MENU_ID, v.so, 1
  FROM (SELECT 'ADMIN_REGIONS' code, 'Regions' nm, '/app/admin/regions' act, 'Locations' grp, 2 so FROM DUAL
        UNION ALL SELECT 'ADMIN_WILAYAS', 'Wilayas', '/app/admin/wilayas', 'Locations', 3 FROM DUAL
        UNION ALL SELECT 'ADMIN_PROVINCES', 'Provinces', '/app/admin/provinces', 'Locations', 4 FROM DUAL
        UNION ALL SELECT 'ADMIN_CITIES', 'Cities', '/app/admin/cities', 'Locations', 5 FROM DUAL
        UNION ALL SELECT 'ADMIN_DIRECTORATES', 'Directorates', '/app/admin/directorates', 'Organisation', 5 FROM DUAL
        UNION ALL SELECT 'ADMIN_GRADES', 'Grades', '/app/admin/grades', 'Employment Masters', 2 FROM DUAL
        UNION ALL SELECT 'ADMIN_CONTRACT_TYPES', 'Contract Types', '/app/admin/contract-types', 'Employment Masters', 3 FROM DUAL
        UNION ALL SELECT 'ADMIN_PROFESSIONS', 'Professions', '/app/admin/professions', 'Employment Masters', 4 FROM DUAL
        UNION ALL SELECT 'ADMIN_EDUCATION_LEVELS', 'Education Levels', '/app/admin/education-levels', 'Employment Masters', 5 FROM DUAL
        UNION ALL SELECT 'ADMIN_NATIONALITIES', 'Nationalities', '/app/admin/nationalities', 'Personal Masters', 1 FROM DUAL
        UNION ALL SELECT 'ADMIN_RELIGIONS', 'Religions', '/app/admin/religions', 'Personal Masters', 2 FROM DUAL
        UNION ALL SELECT 'ADMIN_LANGUAGES', 'Languages', '/app/admin/languages', 'Personal Masters', 3 FROM DUAL
        UNION ALL SELECT 'ADMIN_LANGUAGE_LEVELS', 'Language Levels', '/app/admin/language-levels', 'Personal Masters', 4 FROM DUAL
        UNION ALL SELECT 'ADMIN_HOUSE_TYPES', 'House Types', '/app/admin/house-types', 'Personal Masters', 5 FROM DUAL
        UNION ALL SELECT 'ADMIN_RELATION_TYPES', 'Relation Types', '/app/admin/relation-types', 'Personal Masters', 6 FROM DUAL
        UNION ALL SELECT 'ADMIN_RELATIONS', 'Relations', '/app/admin/relations', 'Personal Masters', 7 FROM DUAL) v
  JOIN HRMS.MAIN_MENU mm ON mm.MAIN_MENU_NAME = v.grp
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = v.code);
-- Expect: 16 rows inserted (0 on a second run).

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 1, 1
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'SYSTEM_ADMIN'
   AND s.SUB_MENU_CODE IN ('ADMIN_REGIONS', 'ADMIN_WILAYAS', 'ADMIN_PROVINCES', 'ADMIN_CITIES', 'ADMIN_DIRECTORATES',
                           'ADMIN_GRADES', 'ADMIN_CONTRACT_TYPES', 'ADMIN_PROFESSIONS', 'ADMIN_EDUCATION_LEVELS',
                           'ADMIN_NATIONALITIES', 'ADMIN_RELIGIONS', 'ADMIN_LANGUAGES', 'ADMIN_LANGUAGE_LEVELS',
                           'ADMIN_HOUSE_TYPES', 'ADMIN_RELATION_TYPES', 'ADMIN_RELATIONS')
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 16 rows inserted (0 on a second run).

COMMIT;
