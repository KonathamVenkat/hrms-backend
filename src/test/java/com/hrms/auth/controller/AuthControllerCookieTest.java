package com.hrms.auth.controller;

import com.hrms.auth.dto.request.LogoutRequest;
import com.hrms.auth.dto.request.RefreshTokenRequest;
import com.hrms.auth.dto.response.LoginResponse;
import com.hrms.auth.security.RefreshCookie;
import com.hrms.auth.service.AuthService;
import com.hrms.common.exception.AccountLockedException;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.common.exception.InvalidTokenException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The refresh token must travel in an HttpOnly cookie and never appear in a response body. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AuthControllerCookieTest.TestConfig.class)
class AuthControllerCookieTest {

    // Not annotated @Configuration so the application's component scan does not pick it up.
    @EnableWebMvc
    static class TestConfig {
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean RefreshCookie refreshCookie() { return new RefreshCookie("hrms_refresh", true, "Strict", "/api/v1/auth", 604_800_000L); }
        @Bean GlobalExceptionHandler handler() { return new GlobalExceptionHandler(); }
        @Bean AuthController authController(AuthService s, RefreshCookie c) { return new AuthController(s, c); }
    }

    @Autowired WebApplicationContext context;
    @Autowired AuthService authService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(authService);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static LoginResponse tokens(String refresh) {
        return LoginResponse.builder().accessToken("access-jwt").refreshToken(refresh)
            .tokenType("Bearer").expiresIn(86400).build();
    }

    @Test
    void loginSetsAnHttpOnlyCookieAndKeepsTheRefreshTokenOutOfTheBody() throws Exception {
        when(authService.login(any(), any())).thenReturn(tokens("refresh-secret"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sara\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
            .andExpect(content().string(not(containsString("refresh-secret"))))
            .andExpect(header().string("Set-Cookie", containsString("hrms_refresh=refresh-secret")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("Secure")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")));
    }

    @Test
    void refreshUsesTheCookieAndReplacesIt() throws Exception {
        when(authService.refreshToken(eq(new RefreshTokenRequest("old-token")), any())).thenReturn(tokens("new-token"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("hrms_refresh", "old-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
            .andExpect(content().string(not(containsString("new-token"))))
            .andExpect(header().string("Set-Cookie", containsString("hrms_refresh=new-token")));
    }

    @Test
    void refreshWithoutACookieIsUnauthorizedNotAServerError() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    void anInvalidRefreshTokenIsUnauthorized() throws Exception {
        when(authService.refreshToken(any(), any())).thenThrow(new InvalidTokenException("Refresh token revoked. Please login again."));

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("hrms_refresh", "stale")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Refresh token revoked. Please login again."));
    }

    @Test
    void logoutRevokesTheCookieTokenAndClearsTheCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("hrms_refresh", "old-token")))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        verify(authService).logout(new LogoutRequest("old-token"));
    }

    @Test
    void logoutWithoutACookieStillSucceeds() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        verifyNoInteractions(authService);
    }

    @Test
    void aLockedAccountIsForbiddenWithItsMessage() throws Exception {
        when(authService.login(any(), any())).thenThrow(new AccountLockedException("Account is locked. Please try again later."));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sara\",\"password\":\"secret123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Account is locked. Please try again later."));
    }
}
