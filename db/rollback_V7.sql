-- ROLLBACK of V7__recruitment_requisitions.sql.
-- DESTROYS every row in the four tables below (requisitions, approvals, stages, rejection reasons). Take a backup first.
-- Stop the backend first. Run as the HRMS user, by hand. Children go before parents.
-- Afterwards remove the history row and the migration file, or the app will not start:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '7';  COMMIT;
-- If later recruitment migrations (V8+) were applied, roll those back first: they point at these tables.

DROP TABLE HRMS.REQUISITION_APPROVALS;
DROP TABLE HRMS.JOB_REQUISITIONS;
DROP TABLE HRMS.REJECTION_REASONS;
DROP TABLE HRMS.RECRUITMENT_STAGES;

DROP SEQUENCE HRMS.SEQ_REC_REQ_CODE;
DROP SEQUENCE HRMS.SEQ_REQUISITION_APPROVALS;
DROP SEQUENCE HRMS.SEQ_JOB_REQUISITIONS;
DROP SEQUENCE HRMS.SEQ_REC_REJECT_REASONS;
DROP SEQUENCE HRMS.SEQ_REC_STAGES;
