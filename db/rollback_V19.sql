-- ROLLBACK of V19__bank_branch_contact.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '19';  COMMIT;
-- and remove the migration file, or the app will not start. The addresses, phones and emails entered meanwhile are lost.
ALTER TABLE HRMS.BANK_BRANCHES DROP (BANK_BRANCHES_ADDRESS, BANK_BRANCHES_PHONE, BANK_BRANCHES_EMAIL);
