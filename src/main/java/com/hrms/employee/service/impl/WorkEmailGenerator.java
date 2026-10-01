package com.hrms.employee.service.impl;

import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.employee.dto.request.CreateEmployeeRequest;
import com.hrms.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Works out the work email for a new employee: the one supplied with the request, or
 * {@code firstname.lastname@<domain>} with a numeric suffix until the address is free.
 *
 * <p>The domain has no default: a wrong domain becomes every employee's stored work email and
 * login address, so the deployment has to state it ({@code hrms.work-email-domain}).
 */
@Component
public class WorkEmailGenerator {

    private final EmployeeRepository employeeRepository;
    private final AuthUserRepository authUserRepository;
    private final String domain;

    public WorkEmailGenerator(
            EmployeeRepository employeeRepository,
            AuthUserRepository authUserRepository,
            @Value("${hrms.work-email-domain}") String domain) {
        this.employeeRepository = employeeRepository;
        this.authUserRepository = authUserRepository;
        this.domain = domain;
    }

    /**
     * If the request already contains a non-blank workEmail (e.g. submitted via API or batch
     * import) that value is used as it is; otherwise one is generated from the name.
     */
    public String resolve(CreateEmployeeRequest request) {
        // Caller supplied a work email — use it (validation annotation ensures format)
        if (StringUtils.hasText(request.getWorkEmail())) {
            return request.getWorkEmail().trim().toLowerCase();
        }

        String local = sanitize(request.getFirstName()) + "." + sanitize(request.getLastName());
        String base = local + "@" + domain;
        if (isFree(base)) {
            return base;
        }

        // Append numeric suffix until unique
        for (int counter = 1; counter <= 999; counter++) {
            String candidate = local + counter + "@" + domain;
            if (isFree(candidate)) {
                return candidate;
            }
        }

        // Extremely unlikely fallback — use username@domain
        return request.getUsername().toLowerCase() + "@" + domain;
    }

    private boolean isFree(String email) {
        return !employeeRepository.existsByWorkEmailIgnoreCase(email)
            && !authUserRepository.existsByEmail(email);
    }

    /** Lowercases and strips characters that are not valid in an email local-part. */
    private static String sanitize(String value) {
        return value.trim()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]", "");
    }
}
