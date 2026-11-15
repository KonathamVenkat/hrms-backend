-- Loads the March 2026 pay scale into HRMS.SALARY_GRADE_SCALE (created by V28): the starting scale for grades 1 to 12,
-- increments 1 to 4 (48 rows), effective from 1 March 2026.
-- Source: Salary_Structure_March_2026_English.xlsx. 44 of the 48 amounts appear in the sheet (each grade and increment had
-- one amount for everybody). The other 4 had no employee in March 2026 and are worked out with the sheet's own rule,
-- each increment = the previous one x 1.05 rounded to a whole number; they also agree with the increment above them:
-- grade 1 increments 2 and 3, grade 10 increment 2, grade 12 increment 3. Marked "derived" below.
-- Not loaded: "Third Range" (one employee, 1,408,564: not a grade in the grade master) and the three top posts with no grade.
-- Grades are found by code (G01 to G12) in HRMS.GRADES.
--
-- Run as the HRMS user AFTER V28. DBeaver: run the whole file with Alt+X, or select from DECLARE down to the "/" line.
-- One block: if a grade code is missing, or the count is not 48 at the end, it stops and saves nothing.
-- Safe to run twice: a grade + increment + date that already exists is skipped. Ends with COMMIT.
-- Undo: db/load_salary_grade_scale_mar2026_rollback.sql
-- Check afterwards:
--   SELECT g.GRADES_CODE, s.INCREMENT_NO, s.STARTING_SCALE FROM HRMS.SALARY_GRADE_SCALE s
--     JOIN HRMS.GRADES g ON g.GRADE_ID = s.GRADE_ID ORDER BY g.SORT_ORDER, s.INCREMENT_NO;

DECLARE
  v_rows NUMBER;
BEGIN
  FOR missing IN (SELECT need.code FROM (SELECT 'G' || LPAD(LEVEL, 2, '0') AS code FROM DUAL CONNECT BY LEVEL <= 12) need
                   WHERE NOT EXISTS (SELECT 1 FROM HRMS.GRADES g WHERE g.GRADES_CODE = need.code)) LOOP
    RAISE_APPLICATION_ERROR(-20001, 'Grade ' || missing.code || ' is not in HRMS.GRADES.');
  END LOOP;
  FOR r IN (
      SELECT 'G01' AS grade_code, 1 AS increment_no, 1224839 AS amount FROM DUAL
      UNION ALL SELECT 'G01' AS grade_code, 2 AS increment_no, 1286081 AS amount FROM DUAL  -- derived: not in the sheet
      UNION ALL SELECT 'G01' AS grade_code, 3 AS increment_no, 1350385 AS amount FROM DUAL  -- derived: not in the sheet
      UNION ALL SELECT 'G01' AS grade_code, 4 AS increment_no, 1417904 AS amount FROM DUAL
      UNION ALL SELECT 'G02' AS grade_code, 1 AS increment_no, 1065077 AS amount FROM DUAL
      UNION ALL SELECT 'G02' AS grade_code, 2 AS increment_no, 1118331 AS amount FROM DUAL
      UNION ALL SELECT 'G02' AS grade_code, 3 AS increment_no, 1174248 AS amount FROM DUAL
      UNION ALL SELECT 'G02' AS grade_code, 4 AS increment_no, 1232960 AS amount FROM DUAL
      UNION ALL SELECT 'G03' AS grade_code, 1 AS increment_no, 926154 AS amount FROM DUAL
      UNION ALL SELECT 'G03' AS grade_code, 2 AS increment_no, 972462 AS amount FROM DUAL
      UNION ALL SELECT 'G03' AS grade_code, 3 AS increment_no, 1021085 AS amount FROM DUAL
      UNION ALL SELECT 'G03' AS grade_code, 4 AS increment_no, 1072139 AS amount FROM DUAL
      UNION ALL SELECT 'G04' AS grade_code, 1 AS increment_no, 805351 AS amount FROM DUAL
      UNION ALL SELECT 'G04' AS grade_code, 2 AS increment_no, 845618 AS amount FROM DUAL
      UNION ALL SELECT 'G04' AS grade_code, 3 AS increment_no, 887899 AS amount FROM DUAL
      UNION ALL SELECT 'G04' AS grade_code, 4 AS increment_no, 932294 AS amount FROM DUAL
      UNION ALL SELECT 'G05' AS grade_code, 1 AS increment_no, 700306 AS amount FROM DUAL
      UNION ALL SELECT 'G05' AS grade_code, 2 AS increment_no, 735321 AS amount FROM DUAL
      UNION ALL SELECT 'G05' AS grade_code, 3 AS increment_no, 772087 AS amount FROM DUAL
      UNION ALL SELECT 'G05' AS grade_code, 4 AS increment_no, 810691 AS amount FROM DUAL
      UNION ALL SELECT 'G06' AS grade_code, 1 AS increment_no, 608961 AS amount FROM DUAL
      UNION ALL SELECT 'G06' AS grade_code, 2 AS increment_no, 639409 AS amount FROM DUAL
      UNION ALL SELECT 'G06' AS grade_code, 3 AS increment_no, 671379 AS amount FROM DUAL
      UNION ALL SELECT 'G06' AS grade_code, 4 AS increment_no, 704948 AS amount FROM DUAL
      UNION ALL SELECT 'G07' AS grade_code, 1 AS increment_no, 529531 AS amount FROM DUAL
      UNION ALL SELECT 'G07' AS grade_code, 2 AS increment_no, 556007 AS amount FROM DUAL
      UNION ALL SELECT 'G07' AS grade_code, 3 AS increment_no, 583808 AS amount FROM DUAL
      UNION ALL SELECT 'G07' AS grade_code, 4 AS increment_no, 612998 AS amount FROM DUAL
      UNION ALL SELECT 'G08' AS grade_code, 1 AS increment_no, 460462 AS amount FROM DUAL
      UNION ALL SELECT 'G08' AS grade_code, 2 AS increment_no, 483485 AS amount FROM DUAL
      UNION ALL SELECT 'G08' AS grade_code, 3 AS increment_no, 507659 AS amount FROM DUAL
      UNION ALL SELECT 'G08' AS grade_code, 4 AS increment_no, 533042 AS amount FROM DUAL
      UNION ALL SELECT 'G09' AS grade_code, 1 AS increment_no, 400402 AS amount FROM DUAL
      UNION ALL SELECT 'G09' AS grade_code, 2 AS increment_no, 420422 AS amount FROM DUAL
      UNION ALL SELECT 'G09' AS grade_code, 3 AS increment_no, 441443 AS amount FROM DUAL
      UNION ALL SELECT 'G09' AS grade_code, 4 AS increment_no, 463515 AS amount FROM DUAL
      UNION ALL SELECT 'G10' AS grade_code, 1 AS increment_no, 333669 AS amount FROM DUAL
      UNION ALL SELECT 'G10' AS grade_code, 2 AS increment_no, 350352 AS amount FROM DUAL  -- derived: not in the sheet
      UNION ALL SELECT 'G10' AS grade_code, 3 AS increment_no, 367870 AS amount FROM DUAL
      UNION ALL SELECT 'G10' AS grade_code, 4 AS increment_no, 386263 AS amount FROM DUAL
      UNION ALL SELECT 'G11' AS grade_code, 1 AS increment_no, 278057 AS amount FROM DUAL
      UNION ALL SELECT 'G11' AS grade_code, 2 AS increment_no, 291960 AS amount FROM DUAL
      UNION ALL SELECT 'G11' AS grade_code, 3 AS increment_no, 306558 AS amount FROM DUAL
      UNION ALL SELECT 'G11' AS grade_code, 4 AS increment_no, 321886 AS amount FROM DUAL
      UNION ALL SELECT 'G12' AS grade_code, 1 AS increment_no, 231714 AS amount FROM DUAL
      UNION ALL SELECT 'G12' AS grade_code, 2 AS increment_no, 243300 AS amount FROM DUAL
      UNION ALL SELECT 'G12' AS grade_code, 3 AS increment_no, 255465 AS amount FROM DUAL  -- derived: not in the sheet
      UNION ALL SELECT 'G12' AS grade_code, 4 AS increment_no, 268238 AS amount FROM DUAL
  ) LOOP
    INSERT INTO HRMS.SALARY_GRADE_SCALE (GRADE_ID, INCREMENT_NO, EFFECTIVE_FROM, STARTING_SCALE, CREATED_BY, CREATED_AT,
                                         UPDATED_AT, VERSION)
    SELECT g.GRADE_ID, r.increment_no, DATE '2026-03-01', r.amount, 'SYSTEM', SYSTIMESTAMP, SYSTIMESTAMP, 0
      FROM HRMS.GRADES g
     WHERE g.GRADES_CODE = r.grade_code
       AND NOT EXISTS (SELECT 1 FROM HRMS.SALARY_GRADE_SCALE x
                        WHERE x.GRADE_ID = g.GRADE_ID AND x.INCREMENT_NO = r.increment_no AND x.EFFECTIVE_FROM = DATE '2026-03-01');
  END LOOP;
  SELECT COUNT(*) INTO v_rows FROM HRMS.SALARY_GRADE_SCALE WHERE EFFECTIVE_FROM = DATE '2026-03-01';
  IF v_rows <> 48 THEN
    RAISE_APPLICATION_ERROR(-20002, 'Expected 48 pay scale rows from 1 March 2026, found ' || v_rows || '. Nothing was saved.');
  END IF;
  DBMS_OUTPUT.PUT_LINE(v_rows || ' pay scale rows');
END;
/

COMMIT;
