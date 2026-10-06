package com.hrms.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What a scheduled job calls when it fails. A job that fails at night otherwise leaves one log
 * line nobody reads, and the next morning's data is silently wrong. A failure is made visible in
 * three places an operator can alert on: an ERROR log line starting {@code JOB_FAILED}, the
 * {@code hrms.job.failures} counter at /actuator/metrics, and a {@code JOB_FAILED} audit row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobAlert {

    private final MeterRegistry meters;
    private final AuditTrail audit;

    public void failed(String job, String detail, Exception cause) {
        log.error("JOB_FAILED job={} {}", job, detail, cause);
        meters.counter("hrms.job.failures", "job", job).increment();
        try {
            audit.recordAs("SYSTEM", "JOB_FAILED", "JOB", job, detail + ": " + cause.getMessage());
        } catch (Exception auditFailure) {
            // The usual cause of a failed job is the database; the log line and counter above still stand.
            log.error("JOB_FAILED could not be written to the audit trail: {}", auditFailure.getMessage());
        }
    }
}
