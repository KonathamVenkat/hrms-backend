-- Run ONCE on each EXISTING database (dev, prod) as the schema owner, after it already has the schema
-- from db/migration/V1__baseline.sql (prod: after prod_catchup_2026_10.sql). It creates Flyway's history
-- table and records "already at version 1", so the app's startup check passes and only scripts numbered
-- V2 and above count as new. It changes no HRMS data. Safe to re-run: it stops if the table exists.
--
-- A brand-new empty database does not need this file: run V1__baseline.sql, then this file.
--
-- From V2 on, each new script is run by hand and then recorded with one more row, e.g.
--   INSERT INTO HRMS."flyway_schema_history" ("installed_rank","version","description","type","script","checksum","installed_by","execution_time","success")
--   VALUES (2, '2', 'add something', 'SQL', 'V2__add_something.sql', 0, 'HRMS', 0, 1);
-- Use 0 as the checksum first. The app then refuses to start and its error prints
--   "-> Resolved locally : <number>": UPDATE the row's "checksum" to that number, and it starts.
-- (Tested: a script in db/migration that is not recorded stops the app with
--  "Detected resolved migration not applied to database: 2".)

CREATE TABLE HRMS."flyway_schema_history" (
    "installed_rank" NUMBER NOT NULL,
    "version"        VARCHAR2(50),
    "description"    VARCHAR2(200) NOT NULL,
    "type"           VARCHAR2(20) NOT NULL,
    "script"         VARCHAR2(1000) NOT NULL,
    "checksum"       NUMBER,
    "installed_by"   VARCHAR2(100) NOT NULL,
    "installed_on"   TIMESTAMP DEFAULT SYSDATE NOT NULL,
    "execution_time" NUMBER NOT NULL,
    "success"        NUMBER(1) NOT NULL,
    CONSTRAINT "flyway_schema_history_pk" PRIMARY KEY ("installed_rank")
);
CREATE INDEX HRMS."flyway_schema_history_s_idx" ON HRMS."flyway_schema_history" ("success");

INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
VALUES (1, '1', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'HRMS', 0, 1);
COMMIT;
