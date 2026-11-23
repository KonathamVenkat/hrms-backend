-- UNDO of the 2026-11-23 dev salary seed: removes the salary records created by the one-off runner (CREATED_BY = 'SEEDSAL').
-- Those employees had no salary before, so no older record has to be made current again. The SALARY_ASSIGNED audit events
-- (actor SEEDSAL) are kept as history. Run as the HRMS user, and only before any payroll run uses these salaries.
DELETE FROM HRMS.EMPLOYEE_SALARY WHERE CREATED_BY = 'SEEDSAL';
COMMIT;
