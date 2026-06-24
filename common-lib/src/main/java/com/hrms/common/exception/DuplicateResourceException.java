package com.hrms.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when attempting to create or update a resource that would violate
 * a uniqueness constraint (e.g., duplicate employee code, email address).
 *
 * <p>Maps to HTTP 409 Conflict.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 *   throw new DuplicateResourceException("Employee", "workEmail", "john.doe@company.com");
 *   // → "Employee already exists with workEmail: 'john.doe@company.com'"
 * }</pre>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public DuplicateResourceException(String message) {
        super(message);
        this.resourceName = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    public String getResourceName() { return resourceName; }
    public String getFieldName()    { return fieldName; }
    public Object getFieldValue()   { return fieldValue; }
}
