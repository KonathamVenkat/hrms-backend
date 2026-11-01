-- ROLLBACK of V16__reference_date_of_birth.sql. Drops the column (the values are lost). Stop the backend first. Run as HRMS by
-- hand. Then: DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '16';  COMMIT;  and remove the migration file.
ALTER TABLE HRMS.EMPLOYEE_REFERENCES DROP (DATE_OF_BIRTH);
