-- Undo db/load_salary_grade_scale_mar2026.sql: removes the pay scale rows effective from 1 March 2026 for grades G01 to G12.
-- The table itself stays (that is V28; see db/rollback_V28.sql). Run as the HRMS user. Ends with COMMIT.
DELETE FROM HRMS.SALARY_GRADE_SCALE
 WHERE EFFECTIVE_FROM = DATE '2026-03-01'
   AND GRADE_ID IN (SELECT GRADE_ID FROM HRMS.GRADES WHERE GRADES_CODE IN ('G01', 'G02', 'G03', 'G04', 'G05', 'G06', 'G07', 'G08', 'G09', 'G10', 'G11', 'G12'));
-- Expect: 48 rows deleted.
COMMIT;
