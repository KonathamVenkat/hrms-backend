package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeAddressRequest;
import com.hrms.employee.dto.request.JobDetailsRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Control and invisible characters are refused in address and job text; ordinary and Arabic text, and line breaks in remarks, pass. */
class AddressJobInputHygieneTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String ZERO_WIDTH_SPACE = "​";

    private static EmployeeAddressRequest address(String line1, String line2) {
        return EmployeeAddressRequest.builder().addressType("CURRENT").addressLine1(line1).addressLine2(line2)
            .city("Juba").country("South Sudan").build();
    }

    private static JobDetailsRequest job(String remarks, String position) {
        return JobDetailsRequest.builder().departmentId(1L).designationId(2L).locationId(3L).workMode("ON_SITE")
            .effectiveFrom(LocalDate.now()).remarks(remarks).jobPositionId(position).build();
    }

    @Test
    void addressTextWithInvisibleOrControlCharactersIsRefused() {
        assertEquals(1, VALIDATOR.validate(address("12 Main" + ZERO_WIDTH_SPACE + " St", null)).size());
        assertEquals(1, VALIDATOR.validate(address("12 Main St", "Block\u0007A")).size());
    }

    @Test
    void ordinaryAndArabicAddressTextIsAccepted() {
        assertTrue(VALIDATOR.validate(address("12 Main St, Plot 4-B", "الطابق الثاني")).isEmpty());
    }

    @Test
    void documentTextRefusesHiddenCharacters_andNotesMayHaveLineBreaks() {
        com.hrms.employee.dto.request.EmployeeDocumentRequest ok = com.hrms.employee.dto.request.EmployeeDocumentRequest.builder()
            .docTypeId(1L).documentName("Passport").documentNumber("P-1").issuedBy("Ministry")
            .notes("line one\nline two").build();
        assertTrue(VALIDATOR.validate(ok).isEmpty());

        com.hrms.employee.dto.request.EmployeeDocumentRequest hidden = com.hrms.employee.dto.request.EmployeeDocumentRequest.builder()
            .docTypeId(1L).documentName("Pass" + ZERO_WIDTH_SPACE + "port").documentNumber("P-1\u0007").issuedBy("Ministry")
            .notes("note" + ZERO_WIDTH_SPACE).build();
        assertEquals(3, VALIDATOR.validate(hidden).size());
    }

    @Test
    void jobRemarksMayHaveLineBreaksButNotOtherControlOrInvisibleCharacters() {
        assertTrue(VALIDATOR.validate(job("First line\r\nSecond line\twith tab", "P-100")).isEmpty());
        assertEquals(1, VALIDATOR.validate(job("hidden" + ZERO_WIDTH_SPACE + "text", "P-100")).size());
        assertEquals(1, VALIDATOR.validate(job("fine", "P-1" + ZERO_WIDTH_SPACE)).size());
    }
}
