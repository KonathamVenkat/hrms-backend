package com.hrms.employee.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigTest {

    @Test
    void originsAreTrimmedAndEmptyEntriesDropped() {
        assertEquals(List.of("https://a.example.com", "https://b.example.com"),
            SecurityConfig.parseOrigins("https://a.example.com, https://b.example.com ,"));
    }

    /** Without the property both local Angular apps (4200 and the redesigned 4201) may call the API. */
    @Test
    void theDefaultAllowsBothLocalAngularPorts() throws Exception {
        String expression = SecurityConfig.class.getDeclaredField("allowedOrigins")
            .getAnnotation(org.springframework.beans.factory.annotation.Value.class).value();
        String fallback = expression.substring(expression.indexOf(':') + 1, expression.length() - 1);
        assertEquals(List.of("http://localhost:4200", "http://localhost:4201"), SecurityConfig.parseOrigins(fallback));
    }
}
