-- V30: SALARY_STRUCTURES.GRADE_ID, the grade a salary structure belongs to. An employee whose job record has a grade
-- is paid on that grade's structure (the salary screen picks it, the service refuses another one). Nullable: a
-- structure for people without a grade (e.g. THIRD_RANGE) has none. At most one structure per grade (unique index;
-- Oracle leaves rows with a NULL key out of it, so any number of structures may have no grade).
-- Schema only. db/link_salary_structures_to_grades.sql sets GRADE_01..GRADE_12 to grades G01..G12 after this script.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v30.sql. Rollback: db/rollback_V30.sql. Additive: the new column is nullable.
ALTER TABLE HRMS.SALARY_STRUCTURES ADD (
	GRADE_ID NUMBER(20,0) CONSTRAINT FK_SAL_STRUCT_GRADE REFERENCES HRMS.GRADES (GRADE_ID)
);
-- Also serves the foreign key.
CREATE UNIQUE INDEX HRMS.UQ_SAL_STRUCT_GRADE ON HRMS.SALARY_STRUCTURES (GRADE_ID);
