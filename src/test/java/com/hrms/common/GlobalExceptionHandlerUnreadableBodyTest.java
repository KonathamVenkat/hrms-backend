package com.hrms.common;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/** An unreadable JSON body gets one fixed message: no class names, no echo of what the caller sent. */
class GlobalExceptionHandlerUnreadableBodyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ResponseEntity<ApiResponse<Void>> handle(String causeText) {
        var ex = new HttpMessageNotReadableException("JSON parse error",
            new RuntimeException(causeText), mock(HttpInputMessage.class));
        return handler.handleMessageNotReadable(ex, new MockHttpServletRequest("POST", "/api/v1/leave/requests"));
    }

    @Test
    void anInvalidEnumValueDoesNotLeakTheClassNameOrTheInput() {
        var response = handle("Cannot deserialize value of type `com.hrms.leave.enums.LeaveStatus` from String \"EVIL\": "
            + "not one of the values accepted for Enum class: [PENDING, APPROVED]");

        assertEquals(400, response.getStatusCode().value());
        String message = response.getBody().getMessage();
        assertFalse(message.contains("com.hrms"));
        assertFalse(message.contains("EVIL"));
        assertFalse(message.contains("LeaveStatus"));
    }

    @Test
    void malformedJsonAndEnumErrorsReturnTheSameMessage() {
        assertEquals(handle("Unexpected character ('}' (code 125))").getBody().getMessage(),
            handle("not one of the values accepted for Enum class: [A, B]").getBody().getMessage());
    }
}
