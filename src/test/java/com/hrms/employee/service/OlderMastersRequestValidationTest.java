package com.hrms.employee.service;

import com.hrms.employee.dto.request.DocumentTypeRequest;
import com.hrms.employee.dto.request.OfficeLocationRequest;
import com.hrms.employee.dto.request.WorkShiftRequest;
import com.hrms.leave.dto.request.LeaveTypeRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Text the older masters accept: lists must be in the shape the code splits them by, names carry no hidden characters. */
class OlderMastersRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> refused(Object r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private WorkShiftRequest shift(String days, String name) {
        return WorkShiftRequest.builder().shiftCode("DAY").shiftName(name).shiftNameAr("x").shiftType("MORNING")
            .startTime("08:00").endTime("16:00").workingHours(new BigDecimal("7.5")).workingDays(days).build();
    }

    private DocumentTypeRequest documentType(String extensions, String name) {
        return DocumentTypeRequest.builder().docTypeCode("CV").docTypeName(name).docTypeNameAr("x").category("OTHER")
            .allowedExtensions(extensions).build();
    }

    @Test
    void workingDays_mustBeTheSevenThreeLetterTokens() {
        assertTrue(refused(shift("MON,TUE,WED,THU,FRI", "Day")).isEmpty());
        assertTrue(refused(shift("sun,mon", "Day")).isEmpty());
        for (String bad : new String[] {"MONDAY,TUESDAY", "MON, TUE", "MON,,TUE", "MON,XYZ", "MON;TUE", ",MON"}) {
            assertEquals(Set.of("workingDays"), refused(shift(bad, "Day")), bad);
        }
    }

    @Test
    void allowedExtensions_mustBeACommaSeparatedListOfExtensions() {
        assertTrue(refused(documentType("PDF,JPG,PNG", "CV")).isEmpty());
        assertTrue(refused(documentType(null, "CV")).isEmpty());
        for (String bad : new String[] {"PDF JPG", "PDF;JPG", ".pdf", "PDF,", "P", "PDF,JPG,EXECUTABLE"}) {
            assertEquals(Set.of("allowedExtensions"), refused(documentType(bad, "CV")), bad);
        }
    }

    @Test
    void namesWithControlOrInvisibleCharacters_areRefused() {
        assertEquals(Set.of("shiftName"), refused(shift("MON", "Bad\u0007name")));
        assertEquals(Set.of("docTypeName"), refused(documentType("PDF", "Zero\u200Bwidth")));
        LeaveTypeRequest leave = LeaveTypeRequest.builder().code("X").nameEn("Bad\u0000").nameAr("x")
            .defaultDays(BigDecimal.ONE).isPaid(true).isCarryForward(false).requiresDocument(false).applicableGender("ALL").build();
        assertEquals(Set.of("nameEn"), refused(leave));
    }

    private OfficeLocationRequest location(String phone, String timezone) {
        return OfficeLocationRequest.builder().locationCode("JUBA").locationName("Juba").locationNameAr("x")
            .locationType("BRANCH").addressLine1("Road").city("Juba").country("South Sudan").phone(phone).timezone(timezone).build();
    }

    @Test
    void officeLocationPhone_mustLookLikeAPhoneNumber() {
        for (String good : new String[] {null, "", "+211 912 345 678", "(0921) 345-678", "0912345678"}) {
            assertTrue(refused(location(good, "Africa/Juba")).isEmpty(), String.valueOf(good));
        }
        for (String bad : new String[] {"call me", "12", "+211;DROP", "<b>1</b>", "1".repeat(31)}) {
            assertEquals(Set.of("phone"), refused(location(bad, "Africa/Juba")), bad);
        }
    }

    @Test
    void officeLocationTimezone_mustBeARealZone() {
        assertTrue(refused(location(null, null)).isEmpty());
        assertTrue(refused(location(null, "Africa/Juba")).isEmpty());
        assertTrue(refused(location(null, "UTC")).isEmpty());
        for (String bad : new String[] {"Juba", "Africa/Nowhere", "GMT+4 please", ""}) {
            assertEquals(Set.of("timezoneKnown"), refused(location(null, bad)), bad);
        }
    }
}
