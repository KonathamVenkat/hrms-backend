-- Links the salary structures from db/salary_structures_by_grade.sql to their grades: GRADE_01 -> G01 ... GRADE_12 -> G12.
-- THIRD_RANGE stays without a grade (it is not a grade in the grade master). Run AFTER V30, as HRMS.
-- Only fills structures that have no grade yet, so it never moves an existing link. Safe to run twice.
-- Check afterwards (expect 12 rows, codes matching):
--   SELECT s.STRUCTURE_CODE, g.GRADES_CODE FROM HRMS.SALARY_STRUCTURES s JOIN HRMS.GRADES g ON g.GRADE_ID = s.GRADE_ID ORDER BY 1;
-- Undo: db/link_salary_structures_to_grades_rollback.sql
UPDATE HRMS.SALARY_STRUCTURES s
   SET s.GRADE_ID = (SELECT g.GRADE_ID FROM HRMS.GRADES g WHERE g.GRADES_CODE = 'G' || SUBSTR(s.STRUCTURE_CODE, 7, 2)),
       s.UPDATED_AT = SYSTIMESTAMP
 WHERE s.GRADE_ID IS NULL
   AND REGEXP_LIKE(s.STRUCTURE_CODE, '^GRADE_(0[1-9]|1[0-2])$')
   AND EXISTS (SELECT 1 FROM HRMS.GRADES g WHERE g.GRADES_CODE = 'G' || SUBSTR(s.STRUCTURE_CODE, 7, 2));
COMMIT;
