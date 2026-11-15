-- Undo db/salary_structures_by_grade.sql: removes the 13 grade structures and their items.
-- Refuses while any employee salary (current or history) points at one of them: nothing is removed in that case.
-- Run as the HRMS user. Ends with COMMIT.
DECLARE
  v_used NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_used FROM HRMS.EMPLOYEE_SALARY e JOIN HRMS.SALARY_STRUCTURES s ON s.STRUCTURE_ID = e.STRUCTURE_ID
   WHERE s.STRUCTURE_CODE IN ('GRADE_01', 'GRADE_02', 'GRADE_03', 'GRADE_04', 'GRADE_05', 'GRADE_06', 'GRADE_07', 'GRADE_08', 'GRADE_09', 'GRADE_10', 'GRADE_11', 'GRADE_12', 'THIRD_RANGE');
  IF v_used > 0 THEN
    RAISE_APPLICATION_ERROR(-20001, v_used || ' employee salary row(s) use these structures: they cannot be removed.');
  END IF;
  DELETE FROM HRMS.SALARY_STRUCTURE_ITEMS
   WHERE STRUCTURE_ID IN (SELECT STRUCTURE_ID FROM HRMS.SALARY_STRUCTURES WHERE STRUCTURE_CODE IN ('GRADE_01', 'GRADE_02', 'GRADE_03', 'GRADE_04', 'GRADE_05', 'GRADE_06', 'GRADE_07', 'GRADE_08', 'GRADE_09', 'GRADE_10', 'GRADE_11', 'GRADE_12', 'THIRD_RANGE'));
  DBMS_OUTPUT.PUT_LINE(SQL%ROWCOUNT || ' item(s) removed');
  DELETE FROM HRMS.SALARY_STRUCTURES WHERE STRUCTURE_CODE IN ('GRADE_01', 'GRADE_02', 'GRADE_03', 'GRADE_04', 'GRADE_05', 'GRADE_06', 'GRADE_07', 'GRADE_08', 'GRADE_09', 'GRADE_10', 'GRADE_11', 'GRADE_12', 'THIRD_RANGE');
  DBMS_OUTPUT.PUT_LINE(SQL%ROWCOUNT || ' structure(s) removed');
END;
/
COMMIT;
