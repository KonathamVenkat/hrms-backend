-- V20: address, phone and email of a company branch (master screen Company Branches), the same three optional details V19 gave
-- bank branches. Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v20.sql. Rollback: db/rollback_V20.sql.
-- Additive and nullable: every existing branch stays valid. English text is VARCHAR2 with CHAR semantics, free text NVARCHAR2.
ALTER TABLE HRMS.COMPANY_BRANCHES ADD (
	COMPANY_BRANCHES_ADDRESS NVARCHAR2(300),
	COMPANY_BRANCHES_PHONE   VARCHAR2(30 CHAR),
	COMPANY_BRANCHES_EMAIL   VARCHAR2(200 CHAR)
);
