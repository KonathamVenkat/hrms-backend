-- V19: address, phone and email of a bank branch (master screen Bank Branches). Not applied yet. Run by hand on HRMS with the
-- backend stopped (Flyway is validate-only), then record it with db/flyway_record_v19.sql. Rollback: db/rollback_V19.sql.
-- Additive and nullable: every existing branch stays valid. English text is VARCHAR2 with CHAR semantics, free text NVARCHAR2.
ALTER TABLE HRMS.BANK_BRANCHES ADD (
	BANK_BRANCHES_ADDRESS NVARCHAR2(300),
	BANK_BRANCHES_PHONE   VARCHAR2(30 CHAR),
	BANK_BRANCHES_EMAIL   VARCHAR2(200 CHAR)
);
