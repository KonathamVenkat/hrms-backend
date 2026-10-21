package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V7 (recruitment requisitions) against a real Oracle schema built from db/migration: the seeded stages and
 * reasons, the one-active-stage-per-terminal-type index, and the CHECK constraints that back the service rules.
 * There are no recruitment entities yet, so this talks to the tables with plain SQL.
 */
@Transactional
class RecruitmentSchemaDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employeeRepository;

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private Long employee() {
        Employee e = employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Lual").firstNameAr("ع").lastName("Deng").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1988, 3, 2)).gender(Gender.MALE)
            .personalEmail("rec-test-p@example.test").workEmail("rec-test-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build());
        return e.getId();
    }

    private long[] org;
    private Long requester;

    /** Inserts a department and a designation once per test; returns {deptId, desigId}. */
    private long[] orgUnit() {
        if (org != null) {
            return org;
        }
        jdbc.update("insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (990001, 'RECT', 'Rec test', N'اختبار')");
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (990001, 'Analyst', 'RECT_AN', 990001)");
        org = new long[] {990001L, 990001L};
        return org;
    }

    private long requisition(String code, String type, Long replaced, int positions, int filled) {
        long[] unit = orgUnit();
        if (requester == null) {
            requester = employee();
        }
        long id = jdbc.queryForObject("select HRMS.SEQ_JOB_REQUISITIONS.NEXTVAL from DUAL", Long.class);
        jdbc.update("""
            insert into HRMS.JOB_REQUISITIONS (REQUISITION_ID, REQUISITION_CODE, JOB_TITLE, DEPARTMENT_ID, DESIGNATION_ID,
                REQUESTED_BY, NO_OF_POSITIONS, FILLED_POSITIONS, EMPLOYMENT_TYPE, REQUISITION_TYPE, REPLACED_EMPLOYEE_ID, CURRENCY)
            values (?, ?, 'Analyst', ?, ?, ?, ?, ?, 'FULL_TIME', ?, ?, 'SSP')
            """, id, code, unit[0], unit[1], requester, positions, filled, type, replaced);
        return id;
    }

    private void approval(long requisitionId, Long approver, String status, int budget, int mgmt, String comments) {
        jdbc.update("""
            insert into HRMS.REQUISITION_APPROVALS (APPROVAL_ID, REQUISITION_ID, APPROVER_ID, STATUS, BUDGET_CONFIRMED,
                MANAGEMENT_CONFIRMED, COMMENTS)
            values (HRMS.SEQ_REQUISITION_APPROVALS.NEXTVAL, ?, ?, ?, ?, ?, ?)
            """, requisitionId, approver, status, budget, mgmt, comments);
    }

    @Test
    void seedHasEightStagesOneActivePerTerminalTypeAndEightReasons() {
        assertEquals(8, count("select count(*) from HRMS.RECRUITMENT_STAGES"));
        for (String type : new String[] {"HIRED", "REJECTED", "WITHDRAWN"}) {
            assertEquals(1, count("select count(*) from HRMS.RECRUITMENT_STAGES where STAGE_TYPE = ? and IS_ACTIVE = 1", type), type);
        }
        assertEquals(2, count("select count(*) from HRMS.RECRUITMENT_STAGES where IS_SYSTEM = 1 and STAGE_TYPE = 'OPEN'"));
        assertEquals(8, count("select count(*) from HRMS.REJECTION_REASONS"));
        assertEquals(0, count("select count(*) from HRMS.REJECTION_REASONS where REASON_CODE = 'NOT_OMANI'"));
    }

    @Test
    void secondActiveStageOfATerminalTypeIsRejectedButAnInactiveOneIsAllowed() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into HRMS.RECRUITMENT_STAGES (STAGE_ID, STAGE_CODE, STAGE_NAME, STAGE_NAME_AR, STAGE_TYPE)
            values (HRMS.SEQ_REC_STAGES.NEXTVAL, 'HIRED_2', 'Hired 2', N'x', 'HIRED')"""));
        assertEquals(1, jdbc.update("""
            insert into HRMS.RECRUITMENT_STAGES (STAGE_ID, STAGE_CODE, STAGE_NAME, STAGE_NAME_AR, STAGE_TYPE, IS_ACTIVE)
            values (HRMS.SEQ_REC_STAGES.NEXTVAL, 'HIRED_OLD', 'Hired old', N'x', 'HIRED', 0)"""));
    }

    @Test
    void stageAndReasonCodesMustBeUpperCaseWithUnderscores() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into HRMS.RECRUITMENT_STAGES (STAGE_ID, STAGE_CODE, STAGE_NAME, STAGE_NAME_AR)
            values (HRMS.SEQ_REC_STAGES.NEXTVAL, 'lower-case', 'x', N'x')"""));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into HRMS.REJECTION_REASONS (REASON_ID, REASON_CODE, REASON_NAME, REASON_NAME_AR)
            values (HRMS.SEQ_REC_REJECT_REASONS.NEXTVAL, 'BAD CODE', 'x', N'x')"""));
    }

    @Test
    void newRequisitionDefaultsToDraftAtVersionZero() {
        long id = requisition("REQ-TEST-000001", "NEW", null, 1, 0);
        assertEquals("DRAFT", jdbc.queryForObject("select STATUS from HRMS.JOB_REQUISITIONS where REQUISITION_ID = ?", String.class, id));
        assertEquals(0L, jdbc.queryForObject("select VERSION from HRMS.JOB_REQUISITIONS where REQUISITION_ID = ?", Long.class, id));
    }

    @Test
    void requisitionRulesAreEnforcedByTheDatabase() {
        assertThrows(DataIntegrityViolationException.class, () -> requisition("REQ-TEST-000002", "NEW", null, 2, 3),
            "filled positions cannot exceed the number of positions");
        assertThrows(DataIntegrityViolationException.class, () -> requisition("REQ-TEST-000003", "REPLACEMENT", null, 1, 0),
            "a replacement names the employee being replaced");
        long id = requisition("REQ-TEST-000004", "NEW", null, 1, 0);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "update HRMS.JOB_REQUISITIONS set MIN_SALARY = 900, MAX_SALARY = 100 where REQUISITION_ID = ?", id),
            "max salary cannot be below min salary");
    }

    @Test
    void approvalNeedsBothConfirmationsAndARejectionNeedsAComment() {
        long id = requisition("REQ-TEST-000005", "NEW", null, 1, 0);
        Long approver = employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Achol").firstNameAr("ع").lastName("Mayen").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1985, 7, 9)).gender(Gender.FEMALE)
            .personalEmail("rec-appr-p@example.test").workEmail("rec-appr-w@example.test")
            .hireDate(LocalDate.of(2023, 2, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();

        assertThrows(DataIntegrityViolationException.class, () -> approval(id, approver, "APPROVED", 1, 0, null));
        assertThrows(DataIntegrityViolationException.class, () -> approval(id, approver, "APPROVED", 0, 1, null));
        assertThrows(DataIntegrityViolationException.class, () -> approval(id, approver, "REJECTED", 0, 0, null));
        assertThrows(DataIntegrityViolationException.class, () -> approval(id, approver, "PENDING", 1, 1, null));

        approval(id, approver, "APPROVED", 1, 1, "Budget line 4 confirmed");
        assertThrows(DataIntegrityViolationException.class, () -> approval(id, approver, "REJECTED", 0, 0, "again"),
            "one decision per level");
        assertEquals(1, count("select count(*) from HRMS.REQUISITION_APPROVALS where REQUISITION_ID = ?", id));
    }
}
