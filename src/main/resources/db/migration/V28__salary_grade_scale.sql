-- V28: SALARY_GRADE_SCALE, the pay scale: the starting scale (basic salary) for each grade and increment (step), from a date.
-- Example from the March 2026 salary sheet: grade 8, increment 1 = 460,462. Each increment is the previous one x 1.05.
-- Effective-dated: a new scale is added with a later EFFECTIVE_FROM; old rows stay, so a past month can still be explained.
-- Schema only. The March 2026 figures are loaded by db/load_salary_grade_scale_mar2026.sql after this script.
-- Nothing reads the table yet (the salary screen still takes the basic as typed); it is the source for that next step.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v28.sql. Rollback: db/rollback_V28.sql. Additive: nothing existing is changed.
CREATE TABLE HRMS.SALARY_GRADE_SCALE (
	GRADE_ID NUMBER(20,0) NOT NULL,
	INCREMENT_NO NUMBER(2,0) NOT NULL,
	EFFECTIVE_FROM DATE NOT NULL,
	STARTING_SCALE NUMBER(12,3) NOT NULL,
	CREATED_BY VARCHAR2(36 CHAR),
	CREATED_AT TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
	UPDATED_BY VARCHAR2(36 CHAR),
	UPDATED_AT TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
	VERSION NUMBER(10,0) DEFAULT 0 NOT NULL,
	-- The primary key's first column is GRADE_ID, so its index also serves the foreign key.
	CONSTRAINT PK_SALARY_GRADE_SCALE PRIMARY KEY (GRADE_ID, INCREMENT_NO, EFFECTIVE_FROM),
	CONSTRAINT FK_SGS_GRADE FOREIGN KEY (GRADE_ID) REFERENCES HRMS.GRADES (GRADE_ID),
	CONSTRAINT CK_SGS_INCREMENT CHECK (INCREMENT_NO >= 1),
	CONSTRAINT CK_SGS_AMOUNT CHECK (STARTING_SCALE > 0),
	-- A date only, never a time of day, so "the scale on date D" has one answer.
	CONSTRAINT CK_SGS_EFFECTIVE_DAY CHECK (EFFECTIVE_FROM = TRUNC(EFFECTIVE_FROM))
);
