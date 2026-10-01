package com.hrms.employee.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Makes the JVM default time zone the company's business zone ({@code hrms.business-zone}).
 *
 * <p>The code uses {@code LocalDate.now()} / {@code LocalDateTime.now()} for "today", effective
 * dates, expiry flags, audit stamps and the nightly attendance jobs. Those follow the JVM zone, so
 * on a UTC server they would be hours off from the people using the system. Setting the default
 * once, before the context starts, fixes all of them and the {@code @Scheduled} cron times.
 */
@Slf4j
public class BusinessTimeZone implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    static final String PROPERTY = "hrms.business-zone";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        apply(event.getEnvironment().getProperty(PROPERTY));
    }

    /** Sets the default zone from {@code zoneId}; leaves the JVM zone alone when it is blank. */
    static void apply(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            log.warn("{} is not set; using the JVM time zone {}", PROPERTY, ZoneId.systemDefault());
            return;
        }
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zoneId.trim())));
        } catch (DateTimeException e) {
            throw new IllegalStateException(PROPERTY + " is not a valid time zone id: " + zoneId, e);
        }
        log.info("Business time zone set to {}", ZoneId.systemDefault());
    }
}
