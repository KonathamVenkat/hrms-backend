-- UNDO of db/seed_sample_employees.sql: removes the five sample employees and their logins (created by SAMPLE5).
-- Run as the HRMS user. If an employee already has other data (job record, addresses, documents, leave...), Oracle refuses the
-- delete (ORA-02292): remove or move that data first. Order: logins before employees.
DELETE FROM HRMS.AUTH_USERS WHERE CREATED_BY = 'SAMPLE5';
DELETE FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SAMPLE5';
COMMIT;
