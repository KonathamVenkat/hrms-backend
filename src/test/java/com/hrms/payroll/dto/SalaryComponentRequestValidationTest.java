package com.hrms.payroll.dto;

import com.hrms.payroll.dto.request.SalaryComponentRequest;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The limits of a salary component that the screen and the database column sizes already imply. */
class SalaryComponentRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> refused(SalaryComponentRequest r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private SalaryComponentRequest request(String code, String name, CalculationType calc, String value, String description) {
        return new SalaryComponentRequest(code, name, "x", ComponentType.EARNING, calc,
            value == null ? null : new BigDecimal(value), false, false, description, 1, null);
    }

    @Test
    void anOrdinaryComponent_isAccepted() {
        assertTrue(refused(request("PASI_EMP", "Employee contribution", CalculationType.PERCENTAGE_OF_BASIC, "7", "Statutory")).isEmpty());
        assertTrue(refused(request("OT_PAY", "Overtime", CalculationType.FORMULA, "0", null)).isEmpty());
    }

    @Test
    void aCodeWithSpacesOrSymbols_isRefused() {
        assertEquals(Set.of("componentCode"), refused(request("BAD CODE", "n", CalculationType.FIXED, "1", null)));
        assertEquals(Set.of("componentCode"), refused(request("A;B", "n", CalculationType.FIXED, "1", null)));
    }

    @Test
    void controlAndInvisibleCharactersInNames_areRefused() {
        assertEquals(Set.of("componentName"), refused(request("A", "Bad\u0000name", CalculationType.FIXED, "1", null)));
        assertEquals(Set.of("componentName"), refused(request("A", "Zero​width", CalculationType.FIXED, "1", null)));
    }

    @Test
    void aDescriptionOverTheColumnSize_isRefused() {
        assertEquals(Set.of("description"), refused(request("A", "n", CalculationType.FIXED, "1", "x".repeat(501))));
    }

    @Test
    void aPercentageAbove100_isRefusedButAFixedAmountIsNot() {
        assertEquals(Set.of("percentageWithinRange"), refused(request("A", "n", CalculationType.PERCENTAGE_OF_GROSS, "150", null)));
        assertEquals(Set.of("percentageWithinRange"), refused(request("A", "n", CalculationType.PERCENTAGE_OF_BASIC, "100.01", null)));
        assertTrue(refused(request("A", "n", CalculationType.PERCENTAGE_OF_BASIC, "100", null)).isEmpty());
        assertTrue(refused(request("A", "n", CalculationType.FIXED, "5000", null)).isEmpty());
    }
}
