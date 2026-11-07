package com.hrms.workflow;

import com.hrms.attendance.entity.OvertimeRequest;
import com.hrms.attendance.enums.OvertimeType;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.attendance.repository.OvertimeRequestRepository;
import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.enums.UserRole;
import com.hrms.common.security.JwtTokenProvider;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A GENERAL_MANAGER counts as an EMPLOYEE (RoleHierarchy) so self-service works on their own records, but gains nothing
 * else: no HR endpoints, no other employee's records. Real security chain and real JWT; rolled back.
 */
@SpringBootTest(classes = EmployeeApplication.class)
@Transactional
class GeneralManagerAccessDbTest extends DatabaseTest {

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider jwt;
    @Autowired EmployeeRepository employees;
    @Autowired AuthUserRepository authUsers;
    @Autowired OvertimeRequestRepository overtimes;

    MockMvc mvc;
    long gm, other;
    private String gmToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        String run = Long.toString(System.nanoTime(), 36);
        gm = person("gm", run, UserRole.GENERAL_MANAGER);
        other = person("oth", run, UserRole.EMPLOYEE);
        String name = authUsers.findByEmployeeId(gm).orElseThrow().getUsername();
        gmToken = jwt.generateAccessToken(name, name + "@example.test", List.of("GENERAL_MANAGER"), gm);
    }

    private long person(String tag, String run, UserRole role) {
        long id = employees.saveAndFlush(Employee.builder()
            .employeeCode("GM-" + tag + "-" + run).firstName("Gm").firstNameAr("ع").lastName(tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
            .personalEmail("gm-" + tag + "-" + run + "-p@example.test")
            .workEmail("gm-" + tag + "-" + run + "-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
        authUsers.saveAndFlush(AuthUser.builder().username("gm-" + tag + "-" + run).email("gm-" + tag + "-" + run + "@example.test")
            .passwordHash("not-a-real-hash").role(role).employeeId(id).fullNameEn("Gm " + tag).isActive(true)
            .isLocked(false).failedAttempts(0).mustChangePassword(0).build());
        return id;
    }

    private ResultActions asGm(org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> r)
            throws Exception {
        return mvc.perform(r.header("Authorization", "Bearer " + gmToken));
    }

    @Test
    void aGeneralManagerUsesTheEmployeeSelfServiceEndpointsOnTheirOwnRecords() throws Exception {
        asGm(get("/api/v1/employees/" + gm)).andExpect(status().isOk());
        asGm(get("/api/v1/employees/" + gm + "/leave-requests")).andExpect(status().isOk());
        asGm(get("/api/v1/employees/" + gm + "/leave-balances/summary")).andExpect(status().isOk());
        asGm(get("/api/v1/attendance/today/" + gm)).andExpect(status().isOk());
        asGm(get("/api/v1/admin/holiday-calendar/active")).andExpect(status().isOk());
    }

    @Test
    void aGeneralManagerStillCannotReadAnotherEmployeesRecords() throws Exception {
        asGm(get("/api/v1/employees/" + other + "/leave-requests")).andExpect(status().isForbidden());
        asGm(get("/api/v1/employees/" + other + "/leave-balances/summary")).andExpect(status().isForbidden());
        asGm(get("/api/v1/attendance/today/" + other)).andExpect(status().isForbidden());
        asGm(get("/api/v1/payroll/employee-salary/" + other + "/current")).andExpect(status().isForbidden());
    }

    @Test
    void aGeneralManagerDoesNotBecomeHrOrAdmin() throws Exception {
        asGm(get("/api/v1/employees/lookup")).andExpect(status().isForbidden());
        asGm(get("/api/v1/employees/" + other + "/exists")).andExpect(status().isForbidden());
        asGm(get("/api/v1/leave-requests")).andExpect(status().isForbidden());
        asGm(get("/api/v1/payroll/employee-salary/" + other + "/history")).andExpect(status().isForbidden());
        asGm(get("/api/v1/admin/departments")).andExpect(status().isForbidden());
        asGm(get("/actuator/info")).andExpect(status().isForbidden());
    }

    @Test
    void aGeneralManagerListsOnlyTheOvertimeOfHrStaff_notOfEmployees() throws Exception {
        long hr = person("hr", Long.toString(System.nanoTime(), 36), UserRole.HR_MANAGER);
        String hrOt = overtime(hr), otherOt = overtime(other);

        String body = asGm(get("/api/v1/attendance/overtime/all")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains(hrOt));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains(otherOt));
        asGm(get("/api/v1/attendance/overtime/all?employeeId=" + other)).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.content").isEmpty());
        asGm(get("/api/v1/attendance/overtime/pending-count")).andExpect(status().isOk());
        asGm(get("/api/v1/attendance/regularization/all")).andExpect(status().isOk());
        asGm(get("/api/v1/attendance/regularization/pending-count")).andExpect(status().isOk());
    }

    private String overtime(long employeeId) {
        String id = "OT-TEST-" + Long.toString(System.nanoTime(), 36);
        OvertimeRequest ot = OvertimeRequest.builder().otId(id).employeeId(employeeId).employeeCode("X")
            .otDate(LocalDate.now().minusDays(1)).otType(OvertimeType.WEEKEND)
            .startTime(LocalDate.now().minusDays(1).atTime(18, 0)).endTime(LocalDate.now().minusDays(1).atTime(20, 0))
            .durationMinutes(120).reason("test").status(RegularizationStatus.PENDING).build();
        ot.setCreatedBy("test");
        ot.setCreatedAt(LocalDateTime.now());
        ot.setUpdatedAt(LocalDateTime.now());
        overtimes.saveAndFlush(ot);
        return id;
    }
}
