-- Armed Forces Support Deduction = 1/30 of gross, as in the March 2026 salary sheet (=Q/30). Run AFTER V31, as HRMS.
-- Changes the rate only where it is still the 3.33 that two decimals allowed: the component's default and every
-- structure item of that component (GRADE_01..GRADE_12 and THIRD_RANGE). Safe to run twice.
-- Salaries assigned before this keep the gross and net they were saved with; assign a new salary to recalculate.
-- Check afterwards (expect 13 rows of 3.3333333333):
--   SELECT s.STRUCTURE_CODE, i.PERCENTAGE FROM HRMS.SALARY_STRUCTURE_ITEMS i
--     JOIN HRMS.SALARY_STRUCTURES s ON s.STRUCTURE_ID = i.STRUCTURE_ID
--     JOIN HRMS.SALARY_COMPONENTS c ON c.COMPONENT_ID = i.COMPONENT_ID
--    WHERE c.COMPONENT_CODE = 'ARMED_FORCES' ORDER BY 1;
-- Undo: db/armed_forces_one_thirtieth_rollback.sql
UPDATE HRMS.SALARY_STRUCTURE_ITEMS i
   SET i.PERCENTAGE = 3.3333333333, i.UPDATED_AT = SYSTIMESTAMP
 WHERE i.PERCENTAGE = 3.33
   AND i.COMPONENT_ID = (SELECT c.COMPONENT_ID FROM HRMS.SALARY_COMPONENTS c WHERE c.COMPONENT_CODE = 'ARMED_FORCES');
UPDATE HRMS.SALARY_COMPONENTS
   SET DEFAULT_VALUE = 3.3333333333, UPDATED_AT = SYSTIMESTAMP
 WHERE COMPONENT_CODE = 'ARMED_FORCES' AND DEFAULT_VALUE = 3.33;
COMMIT;
