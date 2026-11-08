package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V27 against a real Oracle schema: every master has a unique name index, and the index is scoped as the services expect. */
@Transactional
class MastersUniqueNamesDbTest extends DatabaseTest {

    private static final List<String> TABLES = List.of("REGIONS", "RELIGIONS", "NATIONALITIES", "CONTRACT_TYPES",
        "PROFESSIONS", "EDUCATION_LEVELS", "LANGUAGES", "LANGUAGE_LEVELS", "HOUSE_TYPES", "RELATION_TYPES", "BANKS",
        "COMPANIES", "SPECIALIZATIONS", "GRADES", "DEPARTMENTS", "WILAYAS", "PROVINCES", "CITIES", "BANK_BRANCHES",
        "COMPANY_BRANCHES", "DIRECTORATES", "RELATIONS", "SECTIONS", "DESIGNATIONS");

    @Autowired JdbcTemplate jdbc;

    @Test
    void everyMasterHasAUniqueNameIndex() {
        for (String table : TABLES) {
            Integer found = jdbc.queryForObject(
                "select count(*) from user_indexes where table_name = ? and uniqueness = 'UNIQUE' and index_name = ?",
                Integer.class, table, "UQ_" + table + "_NAME");
            assertEquals(1, found, table + " has no unique name index");
        }
    }

    @Test
    void aFlatMasterRefusesTheSameNameInAnyCase() {
        jdbc.update("insert into HRMS.REGIONS (REGION_ID, REGIONS_CODE, REGIONS_NAME, REGIONS_NAME_AR) values (990301, 'V27_A', 'Greater Test', N'x')");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "insert into HRMS.REGIONS (REGION_ID, REGIONS_CODE, REGIONS_NAME, REGIONS_NAME_AR) values (990302, 'V27_B', 'GREATER test', N'x')"));
    }

    @Test
    void aParentedMasterAllowsTheSameNameUnderAnotherParentButNotTwiceInOne() {
        jdbc.update("insert into HRMS.REGIONS (REGION_ID, REGIONS_CODE, REGIONS_NAME, REGIONS_NAME_AR) values (990311, 'V27_R1', 'Region one', N'x')");
        jdbc.update("insert into HRMS.REGIONS (REGION_ID, REGIONS_CODE, REGIONS_NAME, REGIONS_NAME_AR) values (990312, 'V27_R2', 'Region two', N'x')");
        jdbc.update("insert into HRMS.WILAYAS (WILAYA_ID, REGION_ID, WILAYAS_CODE, WILAYAS_NAME, WILAYAS_NAME_AR) values (990321, 990311, 'V27_W1', 'Central', N'x')");
        jdbc.update("insert into HRMS.WILAYAS (WILAYA_ID, REGION_ID, WILAYAS_CODE, WILAYAS_NAME, WILAYAS_NAME_AR) values (990322, 990312, 'V27_W2', 'Central', N'x')");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "insert into HRMS.WILAYAS (WILAYA_ID, REGION_ID, WILAYAS_CODE, WILAYAS_NAME, WILAYAS_NAME_AR) values (990323, 990311, 'V27_W3', 'CENTRAL', N'x')"));
    }

    @Test
    void designationsWithoutADepartmentCountAsOneGroup() {
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE) values (990331, 'Clerk', 'V27_D1')");
        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE) values (990332, 'clerk', 'V27_D2')"));

        jdbc.update("insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (990341, 'V27_DEP', 'Dept', N'x')");
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (990333, 'Clerk', 'V27_D3', 990341)");
        assertTrue(jdbc.queryForObject("select count(*) from HRMS.DESIGNATIONS where upper(TITLE) = 'CLERK'", Integer.class) >= 2);
    }
}
