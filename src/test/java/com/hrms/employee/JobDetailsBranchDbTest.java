package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.projection.JobDetailsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** The job-details queries return the branch and its manager's name (V8), and null when no branch is set. */
@Transactional
class JobDetailsBranchDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EmployeeJobDetailsRepository jobDetailsRepository;

    private Long employee(String suffix) {
        return employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Lual" + suffix).firstNameAr("ع").lastName("Deng").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1988, 3, 2)).gender(Gender.MALE)
            .personalEmail("jdb-p" + suffix + "@example.test").workEmail("jdb-w" + suffix + "@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    /** Department 990101, designation 990101 and office location 990101 (all removed by the test rollback). */
    private void baseFixtures() {
        jdbc.update("insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (990101, 'JDBT', 'JDB test', N'x')");
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (990101, 'Clerk', 'JDBT_CL', 990101)");
        jdbc.update("""
            insert into HRMS.OFFICE_LOCATIONS (LOCATION_ID, LOCATION_CODE, LOCATION_NAME, LOCATION_NAME_AR, ADDRESS_LINE1, CITY)
            values (990101, 'JDBT', 'JDB HQ', N'x', 'Road 1', 'Juba')""");
    }

    private void job(Long employeeId, Long branchId) {
        jdbc.update("""
            insert into HRMS.EMPLOYEE_JOB_DETAILS (JOB_DETAILS_ID, EMPLOYEE_ID, DEPARTMENT_ID, DESIGNATION_ID, LOCATION_ID,
                COMPANY_BRANCH_ID, WORK_MODE, EFFECTIVE_FROM, IS_CURRENT)
            values (HRMS.SEQ_EMPLOYEE_JOB_DETAILS.NEXTVAL, ?, 990101, 990101, 990101, ?, 'ON_SITE', DATE '2024-01-15', 1)
            """, employeeId, branchId);
    }

    @Test
    void currentJobCarriesTheBranchAndItsManagersName() {
        baseFixtures();
        jdbc.update("insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR) values (990101, 'JDBT', 'JDB', N'x')");
        Long manager = employee("m");
        Long worker = employee("w");
        jdbc.update("""
            insert into HRMS.COMPANY_BRANCHES (COMPANY_BRANCH_ID, COMPANY_ID, COMPANY_BRANCHES_CODE, COMPANY_BRANCHES_NAME,
                COMPANY_BRANCHES_NAME_AR, LOCATION_ID, BRANCH_MANAGER_EMPLOYEE_ID)
            values (990101, 990101, 'JDBT_B1', 'Juba Branch', N'فرع', 990101, ?)""", manager);
        job(worker, 990101L);

        JobDetailsProjection p = jobDetailsRepository.findCurrentJobByEmployee(worker).orElseThrow();

        assertEquals(990101L, p.getCompanyBranchId());
        assertEquals(1, jobDetailsRepository.countCurrentJobsInBranch(990101L));
        assertEquals("JDBT_B1", p.getBranchCode());
        assertEquals("Juba Branch", p.getBranchName());
        assertEquals("فرع", p.getBranchNameAr());
        assertEquals("Lualm Deng", p.getBranchManagerName());
        assertEquals(1, jobDetailsRepository.findJobHistoryByEmployee(worker).size());
        assertEquals("JDBT_B1", jobDetailsRepository.findJobHistoryByEmployee(worker).get(0).getBranchCode());
    }

    @Test
    void jobWithoutABranchHasNullBranchFields() {
        baseFixtures();
        Long worker = employee("n");
        job(worker, null);

        JobDetailsProjection p = jobDetailsRepository.findCurrentJobByEmployee(worker).orElseThrow();

        assertNull(p.getCompanyBranchId());
        assertNull(p.getBranchName());
        assertNull(p.getBranchManagerName());
    }

    @Test
    void backFillQueriesListEmployeesWithoutABranchAndSetItOnlyOnce() {
        baseFixtures();
        jdbc.update("insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR) values (990101, 'JDBT', 'JDB', N'x')");
        jdbc.update("""
            insert into HRMS.COMPANY_BRANCHES (COMPANY_BRANCH_ID, COMPANY_ID, COMPANY_BRANCHES_CODE, COMPANY_BRANCHES_NAME,
                COMPANY_BRANCHES_NAME_AR, LOCATION_ID)
            values (990101, 990101, 'JDBT_B1', 'Juba Branch', N'x', 990101)""");
        Long first = employee("q1");
        Long second = employee("q2");
        job(first, null);
        job(second, null);
        long before = jobDetailsRepository.countEmployeesWithoutBranch();

        var all = jobDetailsRepository.findEmployeesWithoutBranch(null, org.springframework.data.domain.PageRequest.of(0, 100));
        assertTrue(all.getContent().stream().anyMatch(r -> r.getEmployeeId().equals(first)));
        assertTrue(all.getContent().stream().anyMatch(r -> r.getEmployeeId().equals(second)));
        assertEquals(before, all.getTotalElements());
        var byName = jobDetailsRepository.findEmployeesWithoutBranch("lualq1", org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, byName.getTotalElements());
        assertEquals("JDB HQ", byName.getContent().get(0).getLocationName());
        assertEquals(0, jobDetailsRepository.findEmployeesWithoutBranch("50\\%", org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());

        var page10 = org.springframework.data.domain.PageRequest.of(0, 10);
        assertEquals(1, jobDetailsRepository.findManagerCandidates(990101L, "lualq1", page10).getTotalElements());
        assertEquals(0, jobDetailsRepository.findManagerCandidates(-1L, "lualq1", page10).getTotalElements(), "other location");
        assertEquals(1, jobDetailsRepository.findManagerCandidates(null, "lualq1", page10).getTotalElements(), "null = anywhere");

        assertEquals(1, jobDetailsRepository.setBranchOnCurrentJob(first, 990101L, "tester"));
        assertEquals(0, jobDetailsRepository.setBranchOnCurrentJob(first, 990101L, "tester"), "second call must change nothing");
        assertEquals(before - 1, jobDetailsRepository.countEmployeesWithoutBranch());
        assertEquals(990101L, jobDetailsRepository.findCurrentJobByEmployee(first).orElseThrow().getCompanyBranchId());
    }
}
