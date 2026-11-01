-- ROLLBACK of V15__fourth_name.sql. Drops the fourth-name columns (the values are lost). Stop the backend first. Run as HRMS by
-- hand. Then: DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '15';  COMMIT;  and remove the migration file.
ALTER TABLE HRMS.EMPLOYEES DROP (FOURTH_NAME, FOURTH_NAME_AR);
ALTER TABLE HRMS.EMPLOYEE_DEPENDENTS DROP (FOURTH_NAME, FOURTH_NAME_AR);
ALTER TABLE HRMS.EMPLOYEE_REFERENCES DROP (FOURTH_NAME, FOURTH_NAME_AR);
