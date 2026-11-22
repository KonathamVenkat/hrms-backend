-- V29: increment (pay-scale step) on EMPLOYEE_JOB_DETAILS, next to GRADE_ID, so each job record says where the
-- employee sits on the pay scale (SALARY_GRADE_SCALE, V28). GRADE_ID, its foreign key FK_EJD_GRADE (V5) and its index
-- IX_FK_EJD_GRADE (V26) already exist; until now nothing wrote GRADE_ID, so every row has it NULL.
-- Both are optional (top posts and contract staff have no grade) but go together: a grade without an increment, or the
-- other way round, is refused. The service also checks that the pair exists on the pay scale; the table cannot,
-- because the scale's key includes its effective date.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v29.sql. Rollback: db/rollback_V29.sql. Additive: the new column is nullable. If the CHECK fails
-- (ORA-02293), some row has a GRADE_ID without an increment: SELECT JOB_DETAILS_ID FROM HRMS.EMPLOYEE_JOB_DETAILS
-- WHERE GRADE_ID IS NOT NULL; then set those to NULL or ask HR for their increment, and run the script again.
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS ADD (
	INCREMENT_NO NUMBER(2,0),
	CONSTRAINT CK_EJD_GRADE_INCREMENT CHECK ((GRADE_ID IS NULL AND INCREMENT_NO IS NULL)
		OR (GRADE_ID IS NOT NULL AND INCREMENT_NO IS NOT NULL AND INCREMENT_NO >= 1))
);
