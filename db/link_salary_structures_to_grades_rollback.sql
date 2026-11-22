-- Undo of db/link_salary_structures_to_grades.sql: removes the grade link of the GRADE_01..GRADE_12 structures.
-- Graded employees then cannot be given a salary until the link is set again. Safe to run twice.
UPDATE HRMS.SALARY_STRUCTURES
   SET GRADE_ID = NULL,
       UPDATED_AT = SYSTIMESTAMP
 WHERE REGEXP_LIKE(STRUCTURE_CODE, '^GRADE_(0[1-9]|1[0-2])$')
   AND GRADE_ID IS NOT NULL;
COMMIT;
