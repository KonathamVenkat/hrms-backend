-- ROLLBACK of V18__drop_identity_document_columns.sql: puts the five empty columns back. Stop the backend first. Run as the HRMS user,
-- by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '18';  COMMIT;
-- and remove the migration file, or the app will not start. The values dropped by V18 are not restored.
ALTER TABLE HRMS.EMPLOYEE_IDENTITY_INFO ADD (
	DOCUMENT_TYPE         NVARCHAR2(100),
	DOCUMENT_NO           VARCHAR2(100 CHAR),
	DOCUMENT_ISSUE_DATE   DATE,
	DOCUMENT_ISSUE_PLACE  NVARCHAR2(100),
	DOCUMENT_VALID_UNTIL  DATE
);
