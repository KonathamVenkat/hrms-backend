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
}
