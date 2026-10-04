package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.service.EmployeePhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role rules and error mapping of the profile photo endpoints. The JWT filter chain is not part
 * of this test.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = EmployeePhotoControllerSecurityTest.TestConfig.class)
class EmployeePhotoControllerSecurityTest {

    // Deliberately not annotated @Configuration, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean EmployeePhotoService photoService() { return mock(EmployeePhotoService.class); }
        @Bean EmployeeAccessGuard accessGuard() { return mock(EmployeeAccessGuard.class); }
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean EmployeePhotoController photoController(EmployeePhotoService s, EmployeeAccessGuard g) {
            return new EmployeePhotoController(s, g);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired EmployeePhotoService photoService;
    @Autowired EmployeeAccessGuard accessGuard;

    MockMvc mvc;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @BeforeEach
    void setUp() {
        reset(photoService, accessGuard);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static MockMultipartHttpServletRequestBuilder upload() {
        MockMultipartHttpServletRequestBuilder put = multipart("/api/v1/employees/5/photo");
        put.file(new MockMultipartFile("file", "me.png", "image/png", PNG));
        put.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        return put;
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void anEmployeeCanReadTheirOwnPhotoWithAnImageTypeAndNoSniffing() throws Exception {
        when(photoService.get(5L)).thenReturn(new EmployeePhotoService.Photo(PNG, "image/png"));

        mvc.perform(get("/api/v1/employees/5/photo"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", containsString("private")))
            .andExpect(content().bytes(PNG));
        verify(accessGuard).assertSelfOrPrivileged(5L);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void anEmployeeCannotReadSomeoneElsesPhoto() throws Exception {
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrPrivileged(6L);

        mvc.perform(get("/api/v1/employees/6/photo")).andExpect(status().isForbidden());
        verify(photoService, never()).get(anyLong());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void anEmployeeCannotChangeSomeoneElsesPhoto() throws Exception {
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrPrivileged(5L);

        mvc.perform(upload()).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/employees/5/photo")).andExpect(status().isForbidden());
        verify(photoService, never()).upload(anyLong(), any());
        verify(photoService, never()).remove(anyLong());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrCanUploadAndRemove() throws Exception {
        mvc.perform(upload()).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/employees/5/photo")).andExpect(status().isOk());

        verify(photoService).upload(eq(5L), any());
        verify(photoService).remove(5L);
    }

    @Test
    @WithMockUser(roles = "SOMEONE_ELSE")
    void anUnknownRoleIsForbiddenOnEveryPhotoEndpoint() throws Exception {
        mvc.perform(get("/api/v1/employees/5/photo")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/employees/5/photo")).andExpect(status().isForbidden());
        verifyNoInteractions(photoService);
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void anEmployeeWithoutAPhotoIsNotFound() throws Exception {
        when(photoService.get(5L)).thenThrow(new ResourceNotFoundException("EmployeePhoto", "employeeId", 5L));

        mvc.perform(get("/api/v1/employees/5/photo")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void aRefusedPhotoIsAnUnprocessableEntityWithTheStandardBody() throws Exception {
        when(photoService.upload(eq(5L), any()))
            .thenThrow(new BusinessRuleException("PHOTO_TOO_LARGE", "The photo must be 2 MB or smaller."));

        mvc.perform(upload())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("The photo must be 2 MB or smaller."));
    }
}
