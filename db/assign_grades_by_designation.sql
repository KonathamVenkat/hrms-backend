-- DEVELOPMENT DATA: gives every CURRENT job record that has NO grade yet a grade and increment from its designation.
-- Mapping agreed with the product owner on 2026-11-23 (Grade 1 = highest pay; only G01-G12 have a pay scale):
--   no grade : Chief Executive Officer, General Manager (top posts, as in the March 2026 salary sheet)
--   G01      : Chief Financial Officer, PMO Director
--   G03      : Head of Department (seeded SDH-...-HEAD), Business Applications / Finance / IT / Supply Chain Manager
--   G05      : Senior Software Engineer, Database Administrator, Senior Accountant
--   G07      : Accountant, Business Analyst, Software Engineer, QA Engineer, Inventory Planner, Logistics Planner,
--              Procurement Specialist, Public Relations Officer, seeded "<Department> Officer" (SDH-...-OFF)
--   G09      : Application Support Engineer, Procurement Officer
--   G10      : Department Secretary (seeded SDH-...-SEC)
--   G11      : Security Officer
-- Increment from full years of service on 2026-11-23 (hire date): under 3 = 1, 3-5 = 2, 6-8 = 3, 9 or more = 4.
-- A row that already has a grade is never touched (changing a set grade needs a new dated job record). The grade+increment pair
-- must exist on the pay scale effective that day, otherwise the row is skipped. Other designations are left without a grade.
-- Changed rows are marked UPDATED_BY = 'GRADEMAP' and their VERSION is bumped (open edit screens then refuse a stale save).
-- Safe to run twice: the second run finds no ungraded mapped rows. Undo: db/assign_grades_by_designation_rollback.sql
-- Run as the HRMS user.

MERGE INTO HRMS.EMPLOYEE_JOB_DETAILS t
USING (
    SELECT m.JOB_DETAILS_ID, gr.GRADE_ID, m.INC
      FROM (SELECT j.JOB_DETAILS_ID,
                   CASE
                     WHEN g.CODE LIKE 'SDH-%-HEAD' THEN 'G03'
                     WHEN g.CODE LIKE 'SDH-%-SEC' THEN 'G10'
                     WHEN g.CODE LIKE 'SDH-%-OFF' THEN 'G07'
                     WHEN UPPER(g.TITLE) IN ('CHIEF FINANCIAL OFFICER', 'PMO DIRECTOR') THEN 'G01'
                     WHEN UPPER(g.TITLE) IN ('BUSINESS APPLICATIONS MANAGER', 'FINANCE MANAGER', 'IT MANAGER',
                                             'SUPPLY CHAIN MANAGER') THEN 'G03'
                     WHEN UPPER(g.TITLE) IN ('SENIOR SOFTWARE ENGINEER', 'DATABASE ADMINISTRATOR', 'SENIOR ACCOUNTANT') THEN 'G05'
                     WHEN UPPER(g.TITLE) IN ('ACCOUNTANT', 'BUSINESS ANALYST', 'SOFTWARE ENGINEER', 'QA ENGINEER',
                                             'INVENTORY PLANNER', 'LOGISTICS PLANNER', 'PROCUREMENT SPECIALIST',
                                             'PUBLIC RELATIONS OFFICER') THEN 'G07'
                     WHEN UPPER(g.TITLE) IN ('APPLICATION SUPPORT ENGINEER', 'PROCUREMENT OFFICER') THEN 'G09'
                     WHEN UPPER(g.TITLE) = 'SECURITY OFFICER' THEN 'G11'
                   END AS GRADE_CODE,
                   CASE
                     WHEN FLOOR(MONTHS_BETWEEN(DATE '2026-11-23', e.HIRE_DATE) / 12) < 3 THEN 1
                     WHEN FLOOR(MONTHS_BETWEEN(DATE '2026-11-23', e.HIRE_DATE) / 12) < 6 THEN 2
                     WHEN FLOOR(MONTHS_BETWEEN(DATE '2026-11-23', e.HIRE_DATE) / 12) < 9 THEN 3
                     ELSE 4
                   END AS INC
              FROM HRMS.EMPLOYEE_JOB_DETAILS j
              JOIN HRMS.EMPLOYEES e ON e.EMPLOYEE_ID = j.EMPLOYEE_ID
              JOIN HRMS.DESIGNATIONS g ON g.DESIG_ID = j.DESIGNATION_ID
             WHERE j.IS_CURRENT = 1
               AND j.GRADE_ID IS NULL
               AND j.INCREMENT_NO IS NULL) m
      JOIN HRMS.GRADES gr ON gr.GRADES_CODE = m.GRADE_CODE AND gr.IS_ACTIVE = 1
     WHERE EXISTS (SELECT 1 FROM HRMS.SALARY_GRADE_SCALE s
                    WHERE s.GRADE_ID = gr.GRADE_ID AND s.INCREMENT_NO = m.INC AND s.EFFECTIVE_FROM <= DATE '2026-11-23')
) src
ON (t.JOB_DETAILS_ID = src.JOB_DETAILS_ID)
WHEN MATCHED THEN UPDATE
   SET t.GRADE_ID = src.GRADE_ID,
       t.INCREMENT_NO = src.INC,
       t.UPDATED_BY = 'GRADEMAP',
       t.UPDATED_AT = SYSTIMESTAMP,
       t.VERSION = NVL(t.VERSION, 0) + 1
 WHERE t.GRADE_ID IS NULL;
-- Expect on dev (2026-11-23): 312 rows (316 current job rows minus CEO, GM and the 2 already graded); 0 on a second run.

COMMIT;
