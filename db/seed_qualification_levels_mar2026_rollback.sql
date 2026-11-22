-- Undo of db/seed_qualification_levels_mar2026.sql: deletes the four seeded levels that no salary uses yet.
-- A level a salary already points to is kept (the foreign key refuses it); deactivate it on the Masters screen instead.
DELETE FROM HRMS.QUALIFICATION_LEVELS q
 WHERE q.QUALIFICATION_LEVELS_CODE IN ('QL_135000', 'QL_195000', 'QL_202500', 'QL_270000')
   AND NOT EXISTS (SELECT 1 FROM HRMS.EMPLOYEE_SALARY s WHERE s.QUALIFICATION_LEVEL_ID = q.QUALIFICATION_LEVEL_ID);
COMMIT;
