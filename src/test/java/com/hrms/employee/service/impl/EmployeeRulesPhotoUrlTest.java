package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The profile photo address is rendered in other users' browsers, so it must stay on the app or be a plain http(s) image. */
class EmployeeRulesPhotoUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/employees/5/photo?v=1", "https://cdn.example.com/a.png", "http://intranet/a.png"})
    void acceptsAnAppPathOrAnHttpAddress(String url) {
        assertThat(EmployeeRules.validatePhotoUrl(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"//attacker.example/p.png", "/\\attacker.example/p.png", "javascript:alert(1)",
        "ftp://host/a.png", "p.png", "/with space", "https://", "/"})
    void rejectsAddressesThatLeaveTheAppOrAreNotImages(String url) {
        assertThatThrownBy(() -> EmployeeRules.validatePhotoUrl(url))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsAnOverlongAddress() {
        String tooLong = "/" + "a".repeat(500);
        assertThatThrownBy(() -> EmployeeRules.validatePhotoUrl(tooLong))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void blankClearsThePhoto() {
        assertThat(EmployeeRules.validatePhotoUrl("  ")).isNull();
        assertThat(EmployeeRules.validatePhotoUrl(null)).isNull();
    }
}
