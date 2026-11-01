-- ROLLBACK of V14__sections_job_details.sql. Removes only the link from job records; the SECTIONS table and its rows are
-- KEPT (dev loaded 244 sections by hand and they are not part of V14 alone). To drop them too, run
-- db/create_sections_nilepet_rollback.sql afterwards. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '14';  COMMIT;
-- and remove the migration file, or the app will not start. Section choices already saved on job records are lost.
DROP INDEX HRMS.IDX_EJD_SECTION;
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS DROP COLUMN SECTION_ID;
