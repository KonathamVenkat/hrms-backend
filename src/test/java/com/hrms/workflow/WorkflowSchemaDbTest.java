package com.hrms.workflow;

import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V10 (approval workflow) against a real Oracle schema built from db/migration: the secretary column, the
 * workflow columns on leave requests, the five workflow tables, one ACTIVE definition per request type,
 * and the GENERAL_MANAGER role.
 */
@SpringBootTest(classes = EmployeeApplication.class)
@Transactional
class WorkflowSchemaDbTest extends DatabaseTest {

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
    void departmentSecretaryColumnExistsAndIsNullable() {
        assertTrue(nullable("DEPARTMENTS", "SECRETARY_EMPLOYEE_ID"));
    }

    @Test
    void leaveRequestWorkflowColumnsExistAndAreNullable() {
        for (String column : new String[] {"WORKFLOW_DEFINITION_ID", "FLOWABLE_PROCESS_INSTANCE_ID", "STAGE",
                "TARGET_DEPARTMENT_ID", "CURRENT_STEP_KEY", "CURRENT_APPROVER_EMPLOYEE_ID", "CURRENT_APPROVER_ROLE"}) {
            assertTrue(nullable("LEAVE_REQUESTS", column), column);
        }
    }

    @Test
    void workflowTablesExist() {
        for (String table : new String[] {"WORKFLOW_DEFINITIONS", "WORKFLOW_STEPS", "WORKFLOW_ROUTES",
                "WORKFLOW_SETTINGS", "REQUEST_HISTORY"}) {
            assertEquals(1, count("select count(*) from ALL_TABLES where OWNER = 'HRMS' and TABLE_NAME = ?", table), table);
        }
    }

    @Test
    void everyNewForeignKeyAndFilterColumnHasAnIndex() {
        for (String index : new String[] {"IDX_DEPT_SECRETARY_ID", "IDX_RH_REQUEST", "IDX_RH_ACTOR",
                "IDX_LR_WF_DEFINITION", "IDX_LR_TARGET_DEPT", "IDX_LR_CUR_APPROVER", "IDX_LR_CUR_ROLE", "IDX_LR_STAGE"}) {
            assertEquals(1, count("select count(*) from ALL_INDEXES where OWNER = 'HRMS' and INDEX_NAME = ?", index), index);
        }
    }

    @Test
    void settingsStartSwitchedOff() {
        for (String key : new String[] {"SYSTEM_ADMIN_CAN_MANAGE_WORKFLOWS", "WORKFLOW_ENABLED_LEAVE"}) {
            assertEquals("false", jdbc.queryForObject(
                "select SETTING_VALUE from HRMS.WORKFLOW_SETTINGS where SETTING_KEY = ?", String.class, key), key);
        }
    }

    @Test
    void onlyOneActiveDefinitionPerRequestType() {
        insertDefinition(990001, "LEAVE", 990001, "ACTIVE");
        insertDefinition(990002, "LEAVE", 990002, "RETIRED");
        insertDefinition(990003, "OVERTIME", 990003, "ACTIVE");
        assertThrows(DataIntegrityViolationException.class, () -> insertDefinition(990004, "LEAVE", 990004, "ACTIVE"));
    }

    @Test
    void generalManagerRoleExistsAndAuthUsersMayHoldIt() {
        assertEquals(1, count("select count(*) from HRMS.ROLES where ROLE_NAME = 'GENERAL_MANAGER'"));
        assertEquals(1, count("select count(*) from ALL_CONSTRAINTS where OWNER = 'HRMS' and TABLE_NAME = 'AUTH_USERS' "
            + "and CONSTRAINT_NAME = 'CK_AUTH_USERS_ROLE' and SEARCH_CONDITION_VC like '%GENERAL_MANAGER%'"));
    }

    @Test
    void historyAcceptsAutoRoutedAndOverridden() {
        insertHistory(990001, "AUTO_ROUTED");
        insertHistory(990002, "OVERRIDDEN");
        assertEquals(2, count("select count(*) from HRMS.REQUEST_HISTORY where HISTORY_ID in (990001, 990002)"));
    }

    @Test
    void historyRejectsUnknownAction() {
        assertThrows(DataIntegrityViolationException.class, () -> insertHistory(990003, "DELETED"));
    }

    @Test
    void secretaryStepWithoutTargetDepartmentPickIsAccepted() {
        insertDefinition(990010, "LEAVE", 990010, "DRAFT");
        insertStep(990010, 990010, "SECRETARY", 1, "DEPARTMENT_SECRETARY", null);
        assertEquals(1, count("select count(*) from HRMS.WORKFLOW_STEPS where STEP_ID = 990010 and PICKS_TARGET_DEPARTMENT = 0"));
    }

    @Test
    void roleStepWithoutRoleIsRejected() {
        insertDefinition(990011, "LEAVE", 990011, "DRAFT");
        assertThrows(DataIntegrityViolationException.class, () -> insertStep(990011, 990011, "HR", 1, "ROLE", null));
    }

    @Test
    void headStepWithRoleIsRejected() {
        insertDefinition(990012, "LEAVE", 990012, "DRAFT");
        assertThrows(DataIntegrityViolationException.class,
            () -> insertStep(990012, 990012, "HEAD", 1, "DEPARTMENT_HEAD", "HR_ADMIN"));
    }

    @Test
    void roleStepWithRoleIsAccepted() {
        insertDefinition(990013, "LEAVE", 990013, "DRAFT");
        insertStep(990013, 990013, "GM", 1, "ROLE", "GENERAL_MANAGER");
        assertEquals(1, count("select count(*) from HRMS.WORKFLOW_STEPS where STEP_ID = 990013"));
    }

    @Test
    void bothSeededSettingsAreStillFalse() {
        assertEquals(2, count("select count(*) from HRMS.WORKFLOW_SETTINGS where SETTING_KEY in "
            + "('SYSTEM_ADMIN_CAN_MANAGE_WORKFLOWS', 'WORKFLOW_ENABLED_LEAVE') and SETTING_VALUE = 'false'"));
    }

    private void insertHistory(long id, String action) {
        jdbc.update("insert into HRMS.REQUEST_HISTORY (HISTORY_ID, REQUEST_TYPE, REQUEST_ID, ACTION) "
            + "values (?, 'LEAVE', 1, ?)", id, action);
    }

    private void insertStep(long id, long definitionId, String key, int position, String type, String role) {
        jdbc.update("insert into HRMS.WORKFLOW_STEPS (STEP_ID, DEFINITION_ID, STEP_KEY, POSITION_NO, LABEL_EN, LABEL_AR, "
            + "APPROVER_TYPE, APPROVER_ROLE) values (?, ?, ?, ?, 'x', N'x', ?, ?)", id, definitionId, key, position, type, role);
    }

    private void insertDefinition(long id, String type, int version, String status) {
        jdbc.update("insert into HRMS.WORKFLOW_DEFINITIONS (DEFINITION_ID, REQUEST_TYPE, VERSION_NO, STATUS) "
            + "values (?, ?, ?, ?)", id, type, version, status);
    }
}
