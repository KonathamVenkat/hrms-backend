package com.hrms.employee.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class BusinessTimeZoneTest {

    private TimeZone original;

    @BeforeEach
    void remember() { original = TimeZone.getDefault(); }

    @AfterEach
    void restore() { TimeZone.setDefault(original); }

    @Test
    void setsTheJvmDefaultZone() {
        BusinessTimeZone.apply("Africa/Juba");
        assertEquals(ZoneId.of("Africa/Juba"), ZoneId.systemDefault());
    }

    @Test
    void blankValueLeavesTheZoneUntouched() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        BusinessTimeZone.apply("  ");
        assertEquals("UTC", TimeZone.getDefault().getID());
    }

    @Test
    void invalidZoneStopsStartup() {
        assertThrows(IllegalStateException.class, () -> BusinessTimeZone.apply("Mars/Olympus"));
    }
}
