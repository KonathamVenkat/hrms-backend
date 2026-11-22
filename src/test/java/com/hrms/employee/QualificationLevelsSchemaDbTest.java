package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.payroll.dto.request.QualificationLevelRequest;
import com.hrms.payroll.service.QualificationLevelService;
import com.hrms.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V32 against a real Oracle schema: the qualification level master (unique code and name, amount above 0) and the
 * allowance columns of a salary (Social above 0; a level and its amount together); a level in use is not deleted.
 */
@Transactional
class QualificationLevelsSchemaDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired QualificationLevelService service;
    @Autowired EmployeeRepository employees;

    private int level(long id, String code, String name, String amount) {
        return jdbc.update("insert into HRMS.QUALIFICATION_LEVELS (QUALIFICATION_LEVEL_ID, QUALIFICATION_LEVELS_CODE, "
            + "QUALIFICATION_LEVELS_NAME, QUALIFICATION_LEVELS_NAME_AR, MONTHLY_AMOUNT) values (?, ?, ?, N'x', " + amount + ")",
            id, code, name);
    }

    /** A salary row of an employee and structure made here (removed by the test rollback). */
    private int salary(long id, String social, Long levelId, String qualificationAmount) {
        return jdbc.update("insert into HRMS.EMPLOYEE_SALARY (EMP_SALARY_ID, EMPLOYEE_ID, STRUCTURE_ID, BASIC_SALARY, "
            + "GROSS_SALARY, NET_SALARY, EFFECTIVE_FROM, IS_CURRENT, SOCIAL_AMOUNT, QUALIFICATION_LEVEL_ID, QUALIFICATION_AMOUNT) "
            + "values (?, ?, 993201, 1, 1, 1, date '2026-11-01', 0, " + social + ", ?, " + qualificationAmount + ")",
            id, employeeId993201, levelId);
    }

    /** Employee and structure 993201 for the salary rows; the employee goes through the entity, like the other tests. */
    private void employeeAndStructure() {
        Long employeeId = employees.saveAndFlush(Employee.builder()
            .firstName("Achol").firstNameAr("ا").lastName("Deng").lastNameAr("د")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.FEMALE)
            .personalEmail("v32-p@example.test").workEmail("v32-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
        employeeId993201 = employeeId;
        jdbc.update("insert into HRMS.SALARY_STRUCTURES (STRUCTURE_ID, STRUCTURE_CODE, STRUCTURE_NAME, VERSION) "
            + "values (993201, 'V32_S', 'V32 structure', 0)");
    }

    private Long employeeId993201;

    @Test
    void levelsHaveAUniqueCodeAndName_andAnAmountAboveZero() {
        assertEquals(1, level(993201, "V32_A", "V32 Bachelor", "135000"));
        assertThrows(DataIntegrityViolationException.class, () -> level(993202, "V32_A", "V32 Other", "1"));
        assertThrows(DataIntegrityViolationException.class, () -> level(993203, "V32_B", "v32 BACHELOR", "1"));
        assertThrows(DataIntegrityViolationException.class, () -> level(993204, "V32_C", "V32 Zero", "0"));
    }

    @Test
    void aSalaryHasSocialAboveZero_andALevelTogetherWithItsAmount() {
        employeeAndStructure();
        level(993201, "V32_A", "V32 Bachelor", "135000");
        assertEquals(1, salary(993201, "135000", 993201L, "135000"));
        assertEquals(1, salary(993202, "null", null, "null"));
        assertThrows(DataIntegrityViolationException.class, () -> salary(993203, "0", null, "null"));
        assertThrows(DataIntegrityViolationException.class, () -> salary(993204, "null", 993201L, "null"));
        assertThrows(DataIntegrityViolationException.class, () -> salary(993205, "null", null, "135000"));
        assertThrows(DataIntegrityViolationException.class, () -> salary(993206, "null", 999999999L, "1"));
    }

    @Test
    void aLevelThatASalaryUsesIsNotDeleted() {
        employeeAndStructure();
        Long id = service.create(new QualificationLevelRequest("V32_IN_USE", "V32 In use", "x",
            new BigDecimal("202500"), 1, true, null)).id();
        salary(993201, "null", id, "202500");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.delete(id));
        assertEquals("MASTER_IN_USE", ex.getRuleCode());
    }
}
