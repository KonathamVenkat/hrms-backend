-- V13: a pay range on each grade (MIN_SALARY / MAX_SALARY) and the profession a designation belongs to
-- (DESIGNATIONS.PROFESSION_ID). Not applied yet. Run by hand on HRMS (Flyway is validate-only), then record it with
-- db/flyway_record_v13.sql. Rollback: db/rollback_V13.sql.
-- Additive: every new column is NULLABLE, so existing grades and designations stay valid. A grade without a range simply
-- has no pay check; a designation without a profession is not constrained to one. Amounts are in the company currency, 3 decimals.
ALTER TABLE HRMS.GRADES ADD (
	MIN_SALARY NUMBER(12,3),
	MAX_SALARY NUMBER(12,3),
	CONSTRAINT CHK_GRADES_SALARY_RANGE CHECK (
		(MIN_SALARY IS NULL OR MIN_SALARY >= 0)
		AND (MAX_SALARY IS NULL OR MAX_SALARY >= 0)
		AND (MIN_SALARY IS NULL OR MAX_SALARY IS NULL OR MAX_SALARY >= MIN_SALARY))
);

ALTER TABLE HRMS.DESIGNATIONS ADD (
	PROFESSION_ID NUMBER(20,0),
	CONSTRAINT FK_DESIG_PROFESSION FOREIGN KEY (PROFESSION_ID) REFERENCES HRMS.PROFESSIONS(PROFESSION_ID)
);
CREATE INDEX IDX_DESIG_PROFESSION ON HRMS.DESIGNATIONS (PROFESSION_ID);
