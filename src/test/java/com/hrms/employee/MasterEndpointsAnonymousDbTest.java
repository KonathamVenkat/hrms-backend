package com.hrms.employee;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The role tests of the master controllers leave out the real security filter chain. This one has it: a caller with no
 * token, or a token that is not valid, gets 401 on every kind of master endpoint (admin masters, set-up lists, payroll
 * set-up) and every verb, before any controller code runs.
 */
class MasterEndpointsAnonymousDbTest extends DatabaseTest {

    private static final List<String> BASES = List.of(
        "/api/v1/admin/departments", "/api/v1/admin/designations", "/api/v1/admin/banks", "/api/v1/admin/regions",
        "/api/v1/admin/cities", "/api/v1/admin/grades", "/api/v1/admin/sections", "/api/v1/admin/document-types",
        "/api/v1/admin/office-locations", "/api/v1/admin/work-shifts", "/api/v1/admin/leave-types",
        "/api/v1/admin/holiday-calendar", "/api/v1/payroll/components", "/api/v1/payroll/structures");

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void noToken_isRefusedWith401OnEveryVerb() throws Exception {
        for (String base : BASES) {
            assertEquals(401, mvc.perform(get(base)).andReturn().getResponse().getStatus(), "GET " + base);
            assertEquals(401, mvc.perform(get(base + "/1")).andReturn().getResponse().getStatus(), "GET " + base + "/1");
            assertEquals(401, mvc.perform(post(base).contentType("application/json").content("{}")).andReturn().getResponse().getStatus(), "POST " + base);
            assertEquals(401, mvc.perform(put(base + "/1").contentType("application/json").content("{}")).andReturn().getResponse().getStatus(), "PUT " + base);
            assertEquals(401, mvc.perform(patch(base + "/1/deactivate")).andReturn().getResponse().getStatus(), "PATCH " + base);
            assertEquals(401, mvc.perform(delete(base + "/1")).andReturn().getResponse().getStatus(), "DELETE " + base);
        }
    }

    @Test
    void aTokenThatIsNotValid_isRefusedWith401() throws Exception {
        for (String base : BASES) {
            assertEquals(401, mvc.perform(get(base).header("Authorization", "Bearer not.a.token"))
                .andReturn().getResponse().getStatus(), "GET " + base);
        }
    }

    @Test
    void theActiveDropdownListsAreNotOpenEither() throws Exception {
        for (String path : List.of("/api/v1/admin/document-types/active", "/api/v1/admin/leave-types/active",
                "/api/v1/admin/holiday-calendar/active", "/api/v1/admin/office-locations/active",
                "/api/v1/admin/work-shifts/active")) {
            assertEquals(401, mvc.perform(get(path)).andReturn().getResponse().getStatus(), path);
        }
    }
}
