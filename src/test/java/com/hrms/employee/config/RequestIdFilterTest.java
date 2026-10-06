package com.hrms.employee.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** Runs a request through the filter and returns {id seen by the code downstream, response}. */
    private String[] run(String suppliedId, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (suppliedId != null) request.addHeader(RequestIdFilter.HEADER, suppliedId);
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));
        return new String[] { seen.get(), response.getHeader(RequestIdFilter.HEADER) };
    }

    @Test
    void aSuppliedIdIsKeptAndEchoed() throws Exception {
        String[] r = run("abc-123", new MockHttpServletResponse());
        assertEquals("abc-123", r[0]);
        assertEquals("abc-123", r[1]);
    }

    @Test
    void aMissingOrUnsafeIdIsReplacedByAGeneratedOne() throws Exception {
        for (String bad : new String[] { null, "has space", "line\nbreak", "x".repeat(65) }) {
            String[] r = run(bad, new MockHttpServletResponse());
            assertNotNull(r[0]);
            assertNotEquals(bad, r[0]);
            assertEquals(r[0], r[1]);
        }
    }

    @Test
    void theIdIsRemovedAfterTheRequest() throws Exception {
        run("abc-123", new MockHttpServletResponse());
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
