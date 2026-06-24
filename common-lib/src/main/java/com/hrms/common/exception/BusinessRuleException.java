package com.hrms.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation violates a domain-level business rule.
 *
 * <p>This is distinct from {@link BadRequestException} (which covers invalid input)
 * and is used for semantic violations such as:
 * <ul>
 *   <li>Attempting to activate a terminated employee</li>
 *   <li>Setting a confirmation date before the probation end date</li>
 *   <li>Applying for leave when employment status is not active</li>
 * </ul>
 * Maps to HTTP 422 Unprocessable Entity.
 * </p>
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleException extends RuntimeException {

    private final String ruleCode;

    public BusinessRuleException(String message) {
        super(message);
        this.ruleCode = null;
    }

    public BusinessRuleException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }
}
