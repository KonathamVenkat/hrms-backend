-- REFERENCE DATA: the job grade scale of the Unified Human Resources Policy Manual (UHRPM-2020, Ministry of Petroleum,
-- South Sudan): Grade 1 (lowest) to Grade 24 (highest), section 8.5/8.6 "Salary Structure".
-- Data only, safe to run twice (a code that exists is skipped). Run as the HRMS user, after V5 (the GRADES table).
-- The manual also says "Grade-25" once; this script follows the main statement (24) so there is no grade 25. Add
-- one more row by hand if the company uses it.
-- The manual applies to every oil and gas operating company, so COMPANY_ID is left empty (valid for any company).
-- Grade names are only "Grade n". The manual gives no job titles per grade; which grade each designation sits on
-- comes from the company's job evaluation, not from this script. Grades are NOT linked to designations here.
-- Bands the manual itself uses (for information; they are not stored):
--   Grade 9 and below            overtime-eligible (section 5.4); Grade 10 and above are not
--   Grade 10 and above           managerial-level transport assistance (section 10.2)
--   Grade 12 and above           professional / scarce-skill allowances
--   Grade 14 to Departmental Manager   "Executive" travel category;  Grade 15 to Departmental Manager   assignment ceiling
-- Arabic text is written with UNISTR escapes so it loads correctly whatever the client encoding is.
-- Undo: db/seed_grades_uhrpm_rollback.sql

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_grade(p_number NUMBER) IS
        v_code   VARCHAR2(30) := 'G' || LPAD(p_number, 2, '0');
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM HRMS.GRADES WHERE GRADES_CODE = v_code;
        IF v_exists = 0 THEN
            INSERT INTO HRMS.GRADES (GRADE_ID, COMPANY_ID, GRADES_CODE, GRADES_NAME, GRADES_NAME_AR, SORT_ORDER, IS_ACTIVE)
            VALUES (HRMS.SEQ_GRADES.NEXTVAL, NULL, v_code, 'Grade ' || p_number,
                    UNISTR('\0627\0644\062F\0631\062C\0629') || N' ' || TO_NCHAR(p_number),   -- الدرجة n
                    p_number, 1);
            DBMS_OUTPUT.PUT_LINE('grade added: ' || v_code);
        ELSE
            DBMS_OUTPUT.PUT_LINE('grade skipped (exists): ' || v_code);
        END IF;
    END;
BEGIN
    FOR n IN 1 .. 24 LOOP
        add_grade(n);
    END LOOP;
    COMMIT;
END;
/
