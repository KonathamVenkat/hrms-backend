package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.service.DepartmentService;
import com.hrms.employee.service.DesignationService;
import com.hrms.employee.service.DocumentTypeService;
import com.hrms.employee.service.OfficeLocationService;
import com.hrms.employee.service.WorkShiftService;
import com.hrms.leave.controller.HolidayCalendarController;
import com.hrms.leave.controller.LeaveTypeController;
import com.hrms.leave.service.HolidayCalendarService;
import com.hrms.leave.service.LeaveTypeService;
import com.hrms.payroll.controller.SalaryComponentController;
import com.hrms.payroll.controller.SalaryStructureController;
import com.hrms.payroll.service.SalaryComponentService;
import com.hrms.payroll.service.SalaryStructureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Who may call the departments, designations, document types, office locations, work shifts, leave types, holidays and
 * payroll set-up endpoints (the admin masters are SYSTEM_ADMIN only; the active lists are dropdowns). Every verb on every one is called as each role and must be refused (403) or allowed. Bodies
 * are valid for each endpoint, because validation runs before the role check. The JWT filter chain is not part of this
 * test (same setup as MasterAdminControllersSecurityTest).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OlderMasterControllersSecurityTest.TestConfig.class)
class OlderMasterControllersSecurityTest {

    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean DepartmentAdminController departments() { return new DepartmentAdminController(mock(DepartmentService.class)); }
        @Bean DesignationAdminController designations() { return new DesignationAdminController(mock(DesignationService.class)); }
        @Bean DocumentTypeController documentTypes() { return new DocumentTypeController(mock(DocumentTypeService.class)); }
        @Bean OfficeLocationController locations() { return new OfficeLocationController(mock(OfficeLocationService.class)); }
        @Bean WorkShiftController shifts() { return new WorkShiftController(mock(WorkShiftService.class)); }
        @Bean LeaveTypeController leaveTypes() { return new LeaveTypeController(mock(LeaveTypeService.class)); }
        @Bean HolidayCalendarController holidays() { return new HolidayCalendarController(mock(HolidayCalendarService.class)); }
        @Bean SalaryComponentController components() { return new SalaryComponentController(mock(SalaryComponentService.class)); }
        @Bean SalaryStructureController structures() { return new SalaryStructureController(mock(SalaryStructureService.class)); }
    }

    /** One endpoint: its method, path and (for POST and PUT) a body that passes validation. */
    private record Call(String method, String path, String body) {
        MockHttpServletRequestBuilder request() {
            MockHttpServletRequestBuilder builder = switch (method) {
                case "GET" -> MockMvcRequestBuilders.get(path);
                case "POST" -> MockMvcRequestBuilders.post(path);
                case "PUT" -> MockMvcRequestBuilders.put(path);
                default -> MockMvcRequestBuilders.patch(path);
            };
            return body == null ? builder : builder.contentType("application/json").content(body);
        }
    }

    private static Call getOf(String path) { return new Call("GET", path, null); }
    private static Call postOf(String path, String body) { return new Call("POST", path, body); }
    private static Call putOf(String path, String body) { return new Call("PUT", path, body); }
    private static Call patchOf(String path) { return new Call("PATCH", path, null); }

    private static final String DEPARTMENT = "{\"code\":\"A1\",\"name\":\"n\",\"nameAr\":\"n\"}";
    private static final String DESIGNATION = "{\"code\":\"A1\",\"title\":\"n\",\"titleAr\":\"n\"}";
    private static final String DOCUMENT_TYPE = "{\"docTypeCode\":\"A1\",\"docTypeName\":\"n\",\"docTypeNameAr\":\"n\",\"category\":\"OTHER\"}";
    private static final String LOCATION = "{\"locationCode\":\"A1\",\"locationName\":\"n\",\"locationNameAr\":\"n\","
        + "\"locationType\":\"BRANCH\",\"addressLine1\":\"a\",\"city\":\"c\",\"country\":\"x\"}";
    private static final String SHIFT = "{\"shiftCode\":\"A1\",\"shiftName\":\"n\",\"shiftNameAr\":\"n\",\"shiftType\":\"MORNING\","
        + "\"startTime\":\"08:00\",\"endTime\":\"16:00\",\"workingHours\":7.5,\"workingDays\":\"MON,TUE\"}";
    private static final String LEAVE_TYPE = "{\"code\":\"A1\",\"nameEn\":\"n\",\"nameAr\":\"n\",\"defaultDays\":1,\"isPaid\":true,"
        + "\"isCarryForward\":false,\"requiresDocument\":false,\"applicableGender\":\"ALL\"}";
    private static final String HOLIDAY = "{\"holidayName\":\"n\",\"holidayNameAr\":\"n\",\"holidayDate\":\"2026-01-01\",\"holidayType\":\"PUBLIC\"}";
    private static final String COMPONENT = "{\"componentCode\":\"A1\",\"componentName\":\"n\",\"componentNameAr\":\"n\","
        + "\"componentType\":\"EARNING\",\"calcType\":\"FIXED\"}";
    private static final String STRUCTURE = "{\"structureCode\":\"A1\",\"structureName\":\"n\",\"items\":[{\"componentId\":1}]}";

    /** Endpoints only SYSTEM_ADMIN may call. */
    private static final List<Call> SYSTEM_ADMIN_ONLY = List.of(
        getOf("/api/v1/admin/departments"), getOf("/api/v1/admin/departments/1"),
        postOf("/api/v1/admin/departments", DEPARTMENT), putOf("/api/v1/admin/departments/1", DEPARTMENT),
        patchOf("/api/v1/admin/departments/1/deactivate"), patchOf("/api/v1/admin/departments/1/activate"),
        getOf("/api/v1/admin/designations"), getOf("/api/v1/admin/designations/1"),
        postOf("/api/v1/admin/designations", DESIGNATION), putOf("/api/v1/admin/designations/1", DESIGNATION),
        patchOf("/api/v1/admin/designations/1/deactivate"), patchOf("/api/v1/admin/designations/1/activate"));

    /** More admin endpoints that only SYSTEM_ADMIN may call (HR_ADMIN used to be allowed on these five masters). */
    private static final List<Call> ALSO_SYSTEM_ADMIN_ONLY = List.of(
        getOf("/api/v1/admin/document-types"), getOf("/api/v1/admin/document-types/1"),
        postOf("/api/v1/admin/document-types", DOCUMENT_TYPE), putOf("/api/v1/admin/document-types/1", DOCUMENT_TYPE),
        patchOf("/api/v1/admin/document-types/1/deactivate"), patchOf("/api/v1/admin/document-types/1/activate"),
        getOf("/api/v1/admin/office-locations"), getOf("/api/v1/admin/office-locations/1"),
        postOf("/api/v1/admin/office-locations", LOCATION), putOf("/api/v1/admin/office-locations/1", LOCATION),
        patchOf("/api/v1/admin/office-locations/1/deactivate"), patchOf("/api/v1/admin/office-locations/1/activate"),
        getOf("/api/v1/admin/work-shifts"), getOf("/api/v1/admin/work-shifts/1"),
        postOf("/api/v1/admin/work-shifts", SHIFT), putOf("/api/v1/admin/work-shifts/1", SHIFT),
        patchOf("/api/v1/admin/work-shifts/1/deactivate"), patchOf("/api/v1/admin/work-shifts/1/activate"),
        getOf("/api/v1/admin/leave-types"), getOf("/api/v1/admin/leave-types/1"),
        postOf("/api/v1/admin/leave-types", LEAVE_TYPE), putOf("/api/v1/admin/leave-types/1", LEAVE_TYPE),
        patchOf("/api/v1/admin/leave-types/1/deactivate"), patchOf("/api/v1/admin/leave-types/1/activate"),
        getOf("/api/v1/admin/holiday-calendar"), getOf("/api/v1/admin/holiday-calendar/1"),
        postOf("/api/v1/admin/holiday-calendar", HOLIDAY), putOf("/api/v1/admin/holiday-calendar/1", HOLIDAY),
        patchOf("/api/v1/admin/holiday-calendar/1/deactivate"), patchOf("/api/v1/admin/holiday-calendar/1/activate"));

    /** Payroll set-up: HR_ADMIN reads and writes, HR_MANAGER reads; SYSTEM_ADMIN and employees get nothing. */
    private static final List<Call> PAYROLL_READS = List.of(
        getOf("/api/v1/payroll/components"), getOf("/api/v1/payroll/components/1"), getOf("/api/v1/payroll/components/type/EARNING"),
        getOf("/api/v1/payroll/structures"), getOf("/api/v1/payroll/structures/1"));
    private static final List<Call> PAYROLL_WRITES = List.of(
        postOf("/api/v1/payroll/components", COMPONENT), putOf("/api/v1/payroll/components/1", COMPONENT),
        patchOf("/api/v1/payroll/components/1/toggle?active=false"),
        postOf("/api/v1/payroll/structures", STRUCTURE), putOf("/api/v1/payroll/structures/1", STRUCTURE),
        patchOf("/api/v1/payroll/structures/1/toggle?active=false"));

    /** Lists that every signed-in role may read (dropdowns). */
    private static final List<Call> ACTIVE_FOR_EVERYONE = List.of(
        getOf("/api/v1/admin/document-types/active"), getOf("/api/v1/admin/leave-types/active"),
        getOf("/api/v1/admin/holiday-calendar/active"));

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** Calls every endpoint; a refused caller gets 403, an allowed one anything but 403 (200, 201 or a body-specific 4xx is not expected). */
    private void check(List<Call> calls, boolean allowed) throws Exception {
        for (Call call : calls) {
            int status = mvc.perform(call.request()).andReturn().getResponse().getStatus();
            String what = call.method() + " " + call.path() + " -> " + status;
            if (allowed) {
                org.junit.jupiter.api.Assertions.assertTrue(status == 200 || status == 201, what);
            } else {
                org.junit.jupiter.api.Assertions.assertEquals(403, status, what);
            }
        }
    }

    @Test @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminMayCallTheAdminEndpoints() throws Exception {
        check(SYSTEM_ADMIN_ONLY, true);
        check(ALSO_SYSTEM_ADMIN_ONLY, true);
        check(ACTIVE_FOR_EVERYONE, true);
    }

    @Test @WithMockUser(roles = "HR_ADMIN")
    void hrAdminMayManagePayrollSetupButNoneOfTheAdminMasters() throws Exception {
        check(SYSTEM_ADMIN_ONLY, false);
        check(ALSO_SYSTEM_ADMIN_ONLY, false);
        check(ACTIVE_FOR_EVERYONE, true);
        check(PAYROLL_READS, true);
        check(PAYROLL_WRITES, true);
    }

    @Test @WithMockUser(roles = "HR_MANAGER")
    void hrManagerMayOnlyReadPayrollSetupAndTheDropdowns() throws Exception {
        check(SYSTEM_ADMIN_ONLY, false);
        check(ALSO_SYSTEM_ADMIN_ONLY, false);
        check(PAYROLL_READS, true);
        check(PAYROLL_WRITES, false);
        check(ACTIVE_FOR_EVERYONE, true);
    }

    @Test @WithMockUser(roles = "EMPLOYEE")
    void anEmployeeMayOnlyReadTheDropdowns() throws Exception {
        check(SYSTEM_ADMIN_ONLY, false);
        check(ALSO_SYSTEM_ADMIN_ONLY, false);
        check(PAYROLL_READS, false);
        check(PAYROLL_WRITES, false);
        check(ACTIVE_FOR_EVERYONE, true);
    }

    @Test @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminHasNoAccessToPayrollSetup() throws Exception {
        check(PAYROLL_READS, false);
        check(PAYROLL_WRITES, false);
    }
}
