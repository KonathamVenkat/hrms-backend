package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The profile photo address is shown in other users' browsers. It may only be the employee's own photo path (what the
 * upload writes), so it can never send a viewer's browser to another site or to another employee's photo.
 */
class EmployeeRulesPhotoUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/employees/5/photo", "/api/v1/employees/5/photo?v=1", "/api/v1/employees/5/photo?v=1762252800123"})
    void acceptsTheEmployeesOwnPhotoPath(String url) {
        assertThat(EmployeeRules.validatePhotoUrl(url, 5L)).isEqualTo(url);
    }

    @Test
    void trimsTheAcceptedValue() {
        assertThat(EmployeeRules.validatePhotoUrl("  /api/v1/employees/5/photo?v=2 ", 5L))
            .isEqualTo("/api/v1/employees/5/photo?v=2");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://cdn.example.com/a.png", "http://intranet/a.png",                    // any other site, even a plain image
        "//attacker.example/p.png", "/\\attacker.example/p.png", "javascript:alert(1)",
        "ftp://host/a.png", "p.png", "/with space", "https://", "/",
        "/api/v1/employees/6/photo?v=1", "/api/v1/employees/55/photo",               // another employee's photo
        "/api/v1/employees/5/photo?v=", "/api/v1/employees/5/photo?v=abc",
        "/api/v1/employees/5/photo?v=1&x=2", "/api/v1/employees/5/photo/../6/photo",
        "/api/v1/employees/5/photo#frag", "/api/v1/employees/5/photos"})
    void rejectsEverythingElse(String url) {
        assertThatThrownBy(() -> EmployeeRules.validatePhotoUrl(url, 5L))
            .isInstanceOf(BusinessRuleException.class)
            .extracting(e -> ((BusinessRuleException) e).getRuleCode()).isEqualTo("INVALID_PHOTO_URL");
    }

    @Test
    void rejectsAnOverlongMarker() {
        assertThatThrownBy(() -> EmployeeRules.validatePhotoUrl("/api/v1/employees/5/photo?v=" + "1".repeat(40), 5L))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void blankClearsThePhoto() {
        assertThat(EmployeeRules.validatePhotoUrl("  ", 5L)).isNull();
        assertThat(EmployeeRules.validatePhotoUrl(null, 5L)).isNull();
    }
}
