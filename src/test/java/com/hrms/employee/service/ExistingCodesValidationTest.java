package com.hrms.employee.service;

import com.hrms.employee.dto.request.DepartmentRequest;
import com.hrms.employee.dto.request.DirectorateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The department and directorate codes already stored are DEP-001 and DIR-001: saving an edit of such a row must not be
 * refused on the code. Anything outside A-Z, 0-9, underscore and hyphen is still refused.
 */
class ExistingCodesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> refusedFields(Object request) {
        return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private DepartmentRequest department(String code) {
        return DepartmentRequest.builder().code(code).name("Finance").nameAr("المالية").build();
    }

    private DirectorateRequest directorate(String code) {
        DirectorateRequest r = new DirectorateRequest();
        r.setCompanyBranchId(1L);
        r.setCode(code);
        r.setName("Operations");
        r.setNameAr("العمليات");
        return r;
    }

    @Test
    void aStoredDepartmentCodeWithAHyphen_isAccepted() {
        assertTrue(refusedFields(department("DEP-001")).isEmpty());
    }

    @Test
    void aStoredDirectorateCodeWithAHyphen_isAccepted() {
        assertTrue(refusedFields(directorate("DIR-001")).isEmpty());
    }

    @Test
    void lowercaseSpacesAndOtherSymbols_stillRefused() {
        for (String bad : new String[] {"dep-001", "DEP 001", "DEP.001", "DEP/001", "DEP-001;"}) {
            assertEquals(Set.of("code"), refusedFields(department(bad)), bad);
            assertEquals(Set.of("code"), refusedFields(directorate(bad)), bad);
        }
    }
}
