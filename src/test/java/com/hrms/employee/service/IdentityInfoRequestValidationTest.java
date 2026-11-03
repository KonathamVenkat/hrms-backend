package com.hrms.employee.service;

import com.hrms.employee.dto.request.IdentityInfoRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The National ID and the passport number are mandatory on every save of the identity details. */
class IdentityInfoRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> refused(IdentityInfoRequest r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private IdentityInfoRequest valid() {
        return IdentityInfoRequest.builder().nationalId("123456789").passportNumber("P1234567").build();
    }

    @Test
    void bothNumbersPresent_isAccepted_withOrWithoutTheOtherDetails() {
        assertTrue(refused(valid()).isEmpty());
        IdentityInfoRequest more = valid();
        more.setTaxId("TAX-1");
        more.setVisaType("Work");
        assertTrue(refused(more).isEmpty());
    }

    @Test
    void aMissingNationalId_isRefused() {
        assertEquals(Set.of("nationalId"), refused(IdentityInfoRequest.builder().passportNumber("P1234567").build()));
    }

    @Test
    void aMissingPassportNumber_isRefused() {
        assertEquals(Set.of("passportNumber"), refused(IdentityInfoRequest.builder().nationalId("123456789").build()));
    }

    @Test
    void emptyAndSpacesOnly_countAsMissing() {
        IdentityInfoRequest r = IdentityInfoRequest.builder().nationalId("").passportNumber("   ").build();
        assertEquals(Set.of("nationalId", "passportNumber"), refused(r));
    }

    @Test
    void anEmptyRequest_isRefusedForBothNumbers() {
        assertEquals(Set.of("nationalId", "passportNumber"), refused(new IdentityInfoRequest()));
    }

    @Test
    void theLengthLimitsStillApply() {
        IdentityInfoRequest r = IdentityInfoRequest.builder().nationalId("1".repeat(201)).passportNumber("P".repeat(200)).build();
        assertEquals(Set.of("nationalId"), refused(r));
    }
}
