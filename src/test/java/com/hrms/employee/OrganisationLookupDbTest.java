package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.OrganisationLookup;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** OrganisationLookup against real rows: branch manager, department head and secretary; inactive means nobody. */
@Transactional
class OrganisationLookupDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired OrganisationLookup lookup;
    @Autowired EntityManager em;

    private Long employee(String suffix) {
        return employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Olk" + suffix).firstNameAr("ع").lastName("Test").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1988, 3, 2)).gender(Gender.MALE)
            .personalEmail("olk-p" + suffix + "@example.test").workEmail("olk-w" + suffix + "@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void fixtures(Long manager, Long head, Long secretary, int deptActive, Long worker) {
        jdbc.update("""
            insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR, HEAD_EMPLOYEE_ID, SECRETARY_EMPLOYEE_ID, IS_ACTIVE)
            values (990201, 'OLKT', 'Olk test', N'x', ?, ?, ?)""", head, secretary, deptActive);
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (990201, 'Clerk', 'OLKT_CL', 990201)");
        jdbc.update("""
            insert into HRMS.OFFICE_LOCATIONS (LOCATION_ID, LOCATION_CODE, LOCATION_NAME, LOCATION_NAME_AR, ADDRESS_LINE1, CITY)
            values (990201, 'OLKT', 'Olk HQ', N'x', 'Road 1', 'Juba')""");
        jdbc.update("insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR) values (990201, 'OLKT', 'Olk', N'x')");
        jdbc.update("""
            insert into HRMS.COMPANY_BRANCHES (COMPANY_BRANCH_ID, COMPANY_ID, COMPANY_BRANCHES_CODE, COMPANY_BRANCHES_NAME,
                COMPANY_BRANCHES_NAME_AR, LOCATION_ID, BRANCH_MANAGER_EMPLOYEE_ID)
            values (990201, 990201, 'OLKT_B1', 'Olk Branch', N'x', 990201, ?)""", manager);
        jdbc.update("""
            insert into HRMS.EMPLOYEE_JOB_DETAILS (JOB_DETAILS_ID, EMPLOYEE_ID, DEPARTMENT_ID, DESIGNATION_ID, LOCATION_ID,
                COMPANY_BRANCH_ID, WORK_MODE, EFFECTIVE_FROM, IS_CURRENT)
            values (HRMS.SEQ_EMPLOYEE_JOB_DETAILS.NEXTVAL, ?, 990201, 990201, 990201, 990201, 'ON_SITE', DATE '2024-01-15', 1)
            """, worker);
    }

    @Test
    void findsBranchManagerHeadAndSecretary() {
        Long manager = employee("m");
        Long head = employee("h");
        Long secretary = employee("s");
        Long worker = employee("w");
        fixtures(manager, head, secretary, 1, worker);

        assertEquals(Optional.of(manager), lookup.branchManagerOf(worker));
        assertEquals(Optional.of(head), lookup.headOf(990201L));
        assertEquals(Optional.of(secretary), lookup.secretaryOf(990201L));
        assertTrue(lookup.departmentExists(990201L));
        assertTrue(lookup.isDepartmentActive(990201L));
        assertFalse(lookup.departmentExists(-1L));
        assertTrue(lookup.branchManagerOf(-1L).isEmpty(), "no current job");
    }

    @Test
    void departmentOfIsTheCurrentJobsActiveDepartment() {
        Long worker = employee("w");
        fixtures(null, null, null, 1, worker);
        assertEquals(Optional.of(990201L), lookup.departmentOf(worker));
        assertTrue(lookup.departmentOf(-1L).isEmpty(), "no current job");

        jdbc.update("update HRMS.DEPARTMENTS set IS_ACTIVE = 0 where DEPT_ID = 990201");
        em.clear();
        assertTrue(lookup.departmentOf(worker).isEmpty(), "inactive department");
    }

    @Test
    void inactiveDepartmentHasNoHeadOrSecretary() {
        Long head = employee("h");
        Long secretary = employee("s");
        fixtures(null, head, secretary, 0, employee("w"));

        assertTrue(lookup.headOf(990201L).isEmpty());
        assertTrue(lookup.secretaryOf(990201L).isEmpty());
        assertTrue(lookup.departmentExists(990201L));
        assertFalse(lookup.isDepartmentActive(990201L));
    }

    @Test
    void inactiveManagerOrHeadIsNobody() {
        Long manager = employee("m");
        Long head = employee("h");
        Long worker = employee("w");
        fixtures(manager, head, null, 1, worker);
        jdbc.update("update HRMS.EMPLOYEES set IS_ACTIVE = 0 where EMPLOYEE_ID in (?, ?)", manager, head);
        em.clear();

        assertTrue(lookup.branchManagerOf(worker).isEmpty());
        assertTrue(lookup.headOf(990201L).isEmpty());
        assertTrue(lookup.secretaryOf(990201L).isEmpty(), "none set");
    }

    @Test
    void branchWithoutManagerOrInactiveBranchIsNobody() {
        Long worker = employee("w");
        fixtures(null, null, null, 1, worker);
        assertTrue(lookup.branchManagerOf(worker).isEmpty(), "no manager");

        Long manager = employee("m");
        jdbc.update("update HRMS.COMPANY_BRANCHES set BRANCH_MANAGER_EMPLOYEE_ID = ?, IS_ACTIVE = 0 where COMPANY_BRANCH_ID = 990201", manager);
        em.clear();
        assertTrue(lookup.branchManagerOf(worker).isEmpty(), "inactive branch");
    }

    @Test
    void employeeWhoseOnlyJobRowIsNotCurrentHasNoBranchManager() {
        Long manager = employee("m");
        Long worker = employee("w");
        fixtures(manager, null, null, 1, worker);
        assertEquals(Optional.of(manager), lookup.branchManagerOf(worker));
        jdbc.update("update HRMS.EMPLOYEE_JOB_DETAILS set IS_CURRENT = 0 where EMPLOYEE_ID = ?", worker);
        em.clear();

        assertTrue(lookup.branchManagerOf(worker).isEmpty());
    }
}
