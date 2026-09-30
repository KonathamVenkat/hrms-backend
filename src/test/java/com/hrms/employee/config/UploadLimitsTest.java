package com.hrms.employee.config;

import com.hrms.common.exception.BusinessRuleException;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadLimitsTest {

    @Test
    void typeLimitBelowCapIsKept_aboveCapIsClamped() {
        UploadLimits limits = new UploadLimits(25);
        assertEquals(10, limits.effectiveMb(10));
        assertEquals(25, limits.effectiveMb(25));
        assertEquals(25, limits.effectiveMb(40));
    }

    @Test
    void raisingTheCapRaisesTheEffectiveLimit() {
        assertEquals(30, new UploadLimits(30).effectiveMb(30));
    }

    @Test
    void perTypeLimitAboveCapIsRejected() {
        UploadLimits limits = new UploadLimits(25);
        assertDoesNotThrow(() -> limits.assertWithinCap(null));
        assertDoesNotThrow(() -> limits.assertWithinCap(25));
        assertThrows(BusinessRuleException.class, () -> limits.assertWithinCap(26));
    }

    @Test
    void multipartLimitFollowsTheCap() {
        MultipartConfigElement cfg = new UploadLimits(30).multipartConfigElement();
        assertEquals(30L * 1024 * 1024, cfg.getMaxFileSize());
        assertEquals(35L * 1024 * 1024, cfg.getMaxRequestSize());
    }

    @Test
    void capBelowOneIsRefused() {
        assertThrows(IllegalStateException.class, () -> new UploadLimits(0));
    }
}
