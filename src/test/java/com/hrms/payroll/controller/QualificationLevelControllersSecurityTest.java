package com.hrms.payroll.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.payroll.dto.response.QualificationLevelResponse;
import com.hrms.payroll.service.QualificationLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The master is SYSTEM_ADMIN's (every verb); the dropdown lookup carries pay amounts and is HR_ADMIN's only. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = QualificationLevelControllersSecurityTest.TestConfig.class)
class QualificationLevelControllersSecurityTest {

    private static final String ADMIN = "/api/v1/admin/qualification-levels";
    private static final String LOOKUP = "/api/v1/payroll/qualification-levels/lookup";
    private static final String BODY = "{\"code\":\"QL_1\",\"name\":\"Bachelor\",\"nameAr\":\"بكالوريوس\","
        + "\"monthlyAmount\":135000,\"sortOrder\":1,\"version\":0}";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean QualificationLevelService service() { return mock(QualificationLevelService.class); }
        @Bean QualificationLevelAdminController admin(QualificationLevelService s) { return new QualificationLevelAdminController(s); }
        @Bean QualificationLevelLookupController lookup(QualificationLevelService s) { return new QualificationLevelLookupController(s); }
    }

    @Autowired WebApplicationContext context;
    @Autowired QualificationLevelService service;
    MockMvc mvc;

    private static final QualificationLevelResponse LEVEL = new QualificationLevelResponse(
        1L, "QL_1", "Bachelor", "بكالوريوس", new BigDecimal("135000.00"), 1, true, null, null, 0L);

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(service.getAll()).thenReturn(List.of(LEVEL));
        when(service.getActive()).thenReturn(List.of(LEVEL));
        when(service.getById(anyLong())).thenReturn(LEVEL);
        when(service.create(any())).thenReturn(LEVEL);
        when(service.update(anyLong(), any())).thenReturn(LEVEL);
    }

    private List<RequestBuilder> everyAdminCall() {
        return List.of(get(ADMIN), get(ADMIN + "/1"),
            post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY),
            put(ADMIN + "/1").contentType(MediaType.APPLICATION_JSON).content(BODY),
            patch(ADMIN + "/1/deactivate"), patch(ADMIN + "/1/activate"), delete(ADMIN + "/1"));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminMaintainsTheMaster_butDoesNotGetTheSalaryLookup() throws Exception {
        for (RequestBuilder call : everyAdminCall()) {
            int code = mvc.perform(call).andReturn().getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertTrue(code == 200 || code == 201, "expected 2xx, got " + code);
        }
        mvc.perform(get(LOOKUP)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminGetsTheLookupWithAmounts_butCannotChangeTheMaster() throws Exception {
        mvc.perform(get(LOOKUP)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].monthlyAmount").value(135000.0));
        reset(service);
        for (RequestBuilder call : everyAdminCall()) {
            mvc.perform(call).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = {"HR_MANAGER", "EMPLOYEE"})
    void otherRolesGetNeither() throws Exception {
        reset(service);
        mvc.perform(get(LOOKUP)).andExpect(status().isForbidden());
        for (RequestBuilder call : everyAdminCall()) {
            mvc.perform(call).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void aZeroOrMissingAmountIsABadRequest() throws Exception {
        reset(service);
        mvc.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON)
            .content(BODY.replace("135000", "0"))).andExpect(status().isBadRequest());
        mvc.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON)
            .content(BODY.replace("\"monthlyAmount\":135000,", ""))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
