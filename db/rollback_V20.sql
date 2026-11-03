-- ROLLBACK of V20__company_branch_contact.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '20';  COMMIT;
-- and remove the migration file, or the app will not start. The addresses, phones and emails entered meanwhile are lost.
ALTER TABLE HRMS.COMPANY_BRANCHES DROP (COMPANY_BRANCHES_ADDRESS, COMPANY_BRANCHES_PHONE, COMPANY_BRANCHES_EMAIL);
