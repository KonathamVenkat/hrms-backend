-- ROLLBACK of V13__grade_salary_range_designation_profession.sql. Drops the grade pay ranges and the designations'
-- profession links (they are lost). Stop the backend first. Run as the HRMS user, by hand. Afterwards remove the
-- history row and the migration file, or the app will not start:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '13';  COMMIT;
-- The code also must go back to the version before this change (the entities map these columns).
DROP INDEX HRMS.IDX_DESIG_PROFESSION;
ALTER TABLE HRMS.DESIGNATIONS DROP (PROFESSION_ID) CASCADE CONSTRAINTS;
ALTER TABLE HRMS.GRADES DROP (MIN_SALARY, MAX_SALARY);
