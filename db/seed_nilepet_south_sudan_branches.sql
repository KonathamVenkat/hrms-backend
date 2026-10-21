-- SAMPLE DATA: Nile Petroleum (company NILPET1), South Sudan: office locations and company branches.
-- Data only, safe to run twice (a code that exists is skipped). Run AFTER V8__organisation_branches.sql, as the HRMS user.
-- These are example sites so the Organisation screens have something to show; change names, add or remove rows to
-- match the real network. Addresses are only the town name, and no GPS position is set (add them in Admin Config >
-- Office Locations). Branch managers are NOT seeded: set them in Organisation > Branch Setup.
-- Arabic text is written with UNISTR escapes so it loads correctly whatever the client encoding is.
-- The comments after each line show the Arabic text.
--
-- What it creates:
--   9 office locations  (1 head office, 2 depots, 6 branches), country South Sudan, time zone Africa/Juba
--   10 company branches (Juba head office holds TWO branches so you can see several branches in one location)
-- Undo: db/seed_nilepet_south_sudan_branches_rollback.sql

SET SERVEROUTPUT ON;

DECLARE
    v_company_id HRMS.COMPANIES.COMPANY_ID%TYPE;

    PROCEDURE add_location(p_code VARCHAR2, p_name VARCHAR2, p_name_ar NVARCHAR2, p_type VARCHAR2,
                           p_city VARCHAR2, p_state VARCHAR2, p_sort NUMBER) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM HRMS.OFFICE_LOCATIONS WHERE LOCATION_CODE = p_code OR LOCATION_NAME = p_name;
        IF v_exists = 0 THEN
            INSERT INTO HRMS.OFFICE_LOCATIONS (LOCATION_ID, LOCATION_CODE, LOCATION_NAME, LOCATION_NAME_AR, LOCATION_TYPE,
                                               ADDRESS_LINE1, CITY, STATE_PROVINCE, COUNTRY, TIMEZONE, IS_ACTIVE, SORT_ORDER)
            VALUES (HRMS.SEQ_OFFICE_LOCATIONS.NEXTVAL, p_code, p_name, p_name_ar, p_type,
                    p_city, p_city, p_state, 'South Sudan', 'Africa/Juba', 1, p_sort);
            DBMS_OUTPUT.PUT_LINE('location added: ' || p_code);
        ELSE
            DBMS_OUTPUT.PUT_LINE('location skipped (exists): ' || p_code);
        END IF;
    END;

    PROCEDURE add_branch(p_code VARCHAR2, p_location_code VARCHAR2, p_name VARCHAR2, p_name_ar NVARCHAR2) IS
        v_exists NUMBER;
        v_location_id HRMS.OFFICE_LOCATIONS.LOCATION_ID%TYPE;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM HRMS.COMPANY_BRANCHES WHERE COMPANY_BRANCHES_CODE = p_code;
        IF v_exists = 0 THEN
            SELECT LOCATION_ID INTO v_location_id FROM HRMS.OFFICE_LOCATIONS WHERE LOCATION_CODE = p_location_code;
            INSERT INTO HRMS.COMPANY_BRANCHES (COMPANY_BRANCH_ID, COMPANY_ID, COMPANY_BRANCHES_CODE, COMPANY_BRANCHES_NAME,
                                               COMPANY_BRANCHES_NAME_AR, LOCATION_ID, IS_ACTIVE)
            VALUES (HRMS.SEQ_COMPANY_BRANCHES.NEXTVAL, v_company_id, p_code, p_name, p_name_ar, v_location_id, 1);
            DBMS_OUTPUT.PUT_LINE('branch added: ' || p_code);
        ELSE
            DBMS_OUTPUT.PUT_LINE('branch skipped (exists): ' || p_code);
        END IF;
    END;
BEGIN
    SELECT COMPANY_ID INTO v_company_id FROM HRMS.COMPANIES WHERE COMPANIES_CODE = 'NILPET1';

    add_location('JUBA_HQ', 'Head Office - Juba', UNISTR('\0627\0644\0645\0643\062A\0628 \0627\0644\0631\0626\064A\0633\064A - \062C\0648\0628\0627'), 'HEAD_OFFICE', 'Juba', 'Central Equatoria', 1);   -- المكتب الرئيسي - جوبا
    add_location('JUBA_DEPOT', 'Juba Fuel Depot', UNISTR('\0645\0633\062A\0648\062F\0639 \0627\0644\0648\0642\0648\062F - \062C\0648\0628\0627'), 'WAREHOUSE', 'Juba', 'Central Equatoria', 2);   -- مستودع الوقود - جوبا
    add_location('WAU', 'Wau Branch', UNISTR('\0641\0631\0639 \0648\0627\0648'), 'BRANCH', 'Wau', 'Western Bahr el Ghazal', 3);   -- فرع واو
    add_location('MALAKAL', 'Malakal Branch', UNISTR('\0641\0631\0639 \0645\0644\0643\0627\0644'), 'BRANCH', 'Malakal', 'Upper Nile', 4);   -- فرع ملكال
    add_location('BOR', 'Bor Branch', UNISTR('\0641\0631\0639 \0628\0648\0631'), 'BRANCH', 'Bor', 'Jonglei', 5);   -- فرع بور
    add_location('YEI', 'Yei Branch', UNISTR('\0641\0631\0639 \064A\0627\064A'), 'BRANCH', 'Yei', 'Central Equatoria', 6);   -- فرع ياي
    add_location('RUMBEK', 'Rumbek Branch', UNISTR('\0641\0631\0639 \0631\0645\0628\064A\0643'), 'BRANCH', 'Rumbek', 'Lakes', 7);   -- فرع رمبيك
    add_location('BENTIU', 'Bentiu Branch', UNISTR('\0641\0631\0639 \0628\0627\0646\062A\064A\0648'), 'BRANCH', 'Bentiu', 'Unity', 8);   -- فرع بانتيو
    add_location('NIMULE', 'Nimule Border Depot', UNISTR('\0645\0633\062A\0648\062F\0639 \0627\0644\062D\062F\0648\062F - \0646\0645\0648\0644\064A'), 'WAREHOUSE', 'Nimule', 'Eastern Equatoria', 9);   -- مستودع الحدود - نمولي

    add_branch('JUBA_HO', 'JUBA_HQ', 'Head Office - Juba', UNISTR('\0627\0644\0645\0643\062A\0628 \0627\0644\0631\0626\064A\0633\064A - \062C\0648\0628\0627'));   -- المكتب الرئيسي - جوبا
    add_branch('JUBA_RETAIL', 'JUBA_HQ', 'Juba Retail Stations', UNISTR('\0645\062D\0637\0627\062A \0627\0644\062A\062C\0632\0626\0629 - \062C\0648\0628\0627'));   -- محطات التجزئة - جوبا
    add_branch('JUBA_DEPOT', 'JUBA_DEPOT', 'Juba Fuel Depot', UNISTR('\0645\0633\062A\0648\062F\0639 \0627\0644\0648\0642\0648\062F - \062C\0648\0628\0627'));   -- مستودع الوقود - جوبا
    add_branch('WAU', 'WAU', 'Wau Branch', UNISTR('\0641\0631\0639 \0648\0627\0648'));   -- فرع واو
    add_branch('MALAKAL', 'MALAKAL', 'Malakal Branch', UNISTR('\0641\0631\0639 \0645\0644\0643\0627\0644'));   -- فرع ملكال
    add_branch('BOR', 'BOR', 'Bor Branch', UNISTR('\0641\0631\0639 \0628\0648\0631'));   -- فرع بور
    add_branch('YEI', 'YEI', 'Yei Branch', UNISTR('\0641\0631\0639 \064A\0627\064A'));   -- فرع ياي
    add_branch('RUMBEK', 'RUMBEK', 'Rumbek Branch', UNISTR('\0641\0631\0639 \0631\0645\0628\064A\0643'));   -- فرع رمبيك
    add_branch('BENTIU', 'BENTIU', 'Bentiu Branch', UNISTR('\0641\0631\0639 \0628\0627\0646\062A\064A\0648'));   -- فرع بانتيو
    add_branch('NIMULE', 'NIMULE', 'Nimule Border Depot', UNISTR('\0645\0633\062A\0648\062F\0639 \0627\0644\062D\062F\0648\062F - \0646\0645\0648\0644\064A'));   -- مستودع الحدود - نمولي

    COMMIT;
END;
/

-- Expect: 9 "location added" and 10 "branch added" lines on the first run, all "skipped" on a second run.

-- ---------------------------------------------------------------------------------------------------------------
-- OPTIONAL (not run by default): your 7 existing employees are on the placeholder location "Head Office - Muscat",
-- so they cannot be given a South Sudan branch yet (a branch must be in the employee's own location). To move them
-- to the Juba head office and retire the Oman placeholder locations, remove the comment marks below and run it.
-- This changes the location on their CURRENT job rows in place (it does not add job history). Then use
-- Organisation > Branch Assignments to give them a branch.
--
-- UPDATE HRMS.EMPLOYEE_JOB_DETAILS
--    SET LOCATION_ID = (SELECT LOCATION_ID FROM HRMS.OFFICE_LOCATIONS WHERE LOCATION_CODE = 'JUBA_HQ')
--  WHERE IS_CURRENT = 1
--    AND LOCATION_ID IN (SELECT LOCATION_ID FROM HRMS.OFFICE_LOCATIONS WHERE LOCATION_CODE IN ('HQ','SLB','SHR','RMT','DQM'));
-- UPDATE HRMS.OFFICE_LOCATIONS SET IS_ACTIVE = 0 WHERE LOCATION_CODE IN ('HQ','SLB','SHR','RMT','DQM');
-- COMMIT;
