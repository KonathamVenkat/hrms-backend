package com.hrms.common;

import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.util.Strings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommonHelpersTest {

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void auditorIsTheSignedInUser() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user-123", null, AuthorityUtils.createAuthorityList("ROLE_HR_ADMIN")));
        assertEquals("user-123", CurrentAuditor.name());
    }

    @Test
    void auditorIsSystemWithoutASignedInUser() {
        assertEquals("SYSTEM", CurrentAuditor.name());
    }

    @Test
    void auditorIsSystemForAnAnonymousRequest() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals("SYSTEM", CurrentAuditor.name());
    }

    @Test
    void trimToNullTrimsAndTurnsBlankIntoNull() {
        assertEquals("abc", Strings.trimToNull("  abc "));
        assertNull(Strings.trimToNull("   "));
        assertNull(Strings.trimToNull(null));
    }
}
