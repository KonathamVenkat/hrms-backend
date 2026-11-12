package com.hrms.payroll.dto;

import com.hrms.leave.dto.request.LeaveTypeRequest;
import com.hrms.payroll.dto.request.SalaryStructureItemRequest;
import com.hrms.payroll.dto.request.SalaryStructureRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Salary structures follow the same input rules as the other masters; leave types have upper bounds on their day counts. */
class SalaryStructureAndLeaveTypeLimitsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> refused(Object request) {
        return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static SalaryStructureItemRequest item(Integer sortOrder) {
        return new SalaryStructureItemRequest(5L, null, BigDecimal.TEN, BigDecimal.ZERO, sortOrder);
    }

    private static SalaryStructureRequest structure(String code, String name, String description, List<SalaryStructureItemRequest> items) {
        return new SalaryStructureRequest(code, name, description, items, null);
    }

    @Test
    void anOrdinaryStructure_isAccepted() {
        assertTrue(refused(structure("STD_2026", "Standard", "Grades 1 to 3", List.of(item(1)))).isEmpty());
        assertTrue(refused(structure("GRADE-A", "Grade A", null, List.of(item(1)))).isEmpty());
    }

    @Test
    void aStructureCodeWithSpacesOrSymbols_isRefused() {
        assertEquals(Set.of("structureCode"), refused(structure("BAD CODE", "n", null, List.of(item(1)))));
        assertEquals(Set.of("structureCode"), refused(structure("A;B", "n", null, List.of(item(1)))));
    }

    @Test
    void controlAndInvisibleCharactersInTheNameOrDescription_areRefused() {
        assertEquals(Set.of("structureName"), refused(structure("A", "Bad\u0000name", null, List.of(item(1)))));
        assertEquals(Set.of("structureName"), refused(structure("A", "Zero​width", null, List.of(item(1)))));
        assertEquals(Set.of("description"), refused(structure("A", "n", "bad\u0007text", List.of(item(1)))));
    }

    @Test
    void aDescriptionWithLineBreaks_isAccepted_andOneOverTheColumnSizeIsRefused() {
        assertTrue(refused(structure("A", "n", "line one\r\nline two", List.of(item(1)))).isEmpty());
        assertEquals(Set.of("description"), refused(structure("A", "n", "x".repeat(501), List.of(item(1)))));
    }

    @Test
    void moreThanAHundredItems_orAnOutOfRangeSortOrder_areRefused() {
        assertEquals(Set.of("items"), refused(structure("A", "n", null, Collections.nCopies(101, item(1)))));
        assertEquals(Set.of("items[0].sortOrder"), refused(structure("A", "n", null, List.of(item(-1)))));
        assertEquals(Set.of("items[0].sortOrder"), refused(structure("A", "n", null, List.of(item(1001)))));
    }

    private static LeaveTypeRequest.LeaveTypeRequestBuilder leaveType() {
        return LeaveTypeRequest.builder().code("ANNUAL").nameEn("Annual").nameAr("x").defaultDays(new BigDecimal("21"))
            .isPaid(true).isCarryForward(true).requiresDocument(false).applicableGender("ALL");
    }

    @Test
    void leaveTypeDayCountsUpTo365_areAccepted_andAboveAreRefused() {
        assertTrue(refused(leaveType().maxCarryDays(new BigDecimal("365")).minNoticeDays(365).maxConsecutiveDays(365).build()).isEmpty());
        assertEquals(Set.of("maxCarryDays"), refused(leaveType().maxCarryDays(new BigDecimal("366")).build()));
        assertEquals(Set.of("minNoticeDays"), refused(leaveType().minNoticeDays(366).build()));
        assertEquals(Set.of("maxConsecutiveDays"), refused(leaveType().maxConsecutiveDays(366).build()));
    }

    @Test
    void aCompensatoryLeaveTypeWhoseCarryExceedsItsDefaultDays_staysValid() {
        assertTrue(refused(leaveType().defaultDays(BigDecimal.ZERO).maxCarryDays(new BigDecimal("30")).build()).isEmpty());
    }
}
