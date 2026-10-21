package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V8 (organisation structure) against a real Oracle schema built from db/migration: a branch sits in a location
 * and has one manager, an employee's job row points at a branch, and every new foreign key is indexed.
 */
@Transactional
class OrganisationSchemaDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private boolean nullable(String table, String column) {
        return "Y".equals(jdbc.queryForObject(
            "select NULLABLE from ALL_TAB_COLUMNS where OWNER = 'HRMS' and TABLE_NAME = ? and COLUMN_NAME = ?",
            String.class, table, column));
    }

    @Test
    void newColumnsExistAndAreNullable() {
        assertTrue(nullable("COMPANY_BRANCHES", "LOCATION_ID"));
        assertTrue(nullable("COMPANY_BRANCHES", "BRANCH_MANAGER_EMPLOYEE_ID"));
        assertTrue(nullable("EMPLOYEE_JOB_DETAILS", "COMPANY_BRANCH_ID"));
    }

    @Test
    void everyNewForeignKeyHasAnIndex() {
        for (String index : new String[] {"IDX_CB_LOCATION_ID", "IDX_CB_MANAGER_ID", "IDX_EJD_BRANCH_ID"}) {
            assertEquals(1, count("select count(*) from ALL_INDEXES where OWNER = 'HRMS' and INDEX_NAME = ?", index), index);
        }
        for (String fk : new String[] {"FK_CB_LOCATION", "FK_CB_MANAGER", "FK_EJD_BRANCH"}) {
            assertEquals(1, count("select count(*) from ALL_CONSTRAINTS where OWNER = 'HRMS' and CONSTRAINT_NAME = ? "
                + "and CONSTRAINT_TYPE = 'R'", fk), fk);
        }
    }

    @Test
    void aBranchCannotPointAtAnUnknownLocationOrManager() {
        jdbc.update("""
            insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR)
            values (990001, 'ORGT', 'Org test', N'x')""");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into HRMS.COMPANY_BRANCHES (COMPANY_BRANCH_ID, COMPANY_ID, COMPANY_BRANCHES_CODE, COMPANY_BRANCHES_NAME,
                COMPANY_BRANCHES_NAME_AR, LOCATION_ID)
            values (990001, 990001, 'ORGT_B1', 'Branch', N'x', 999999999)"""));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into HRMS.COMPANY_BRANCHES (COMPANY_BRANCH_ID, COMPANY_ID, COMPANY_BRANCHES_CODE, COMPANY_BRANCHES_NAME,
                COMPANY_BRANCHES_NAME_AR, BRANCH_MANAGER_EMPLOYEE_ID)
            values (990002, 990001, 'ORGT_B2', 'Branch', N'x', 999999999)"""));
    }
}
