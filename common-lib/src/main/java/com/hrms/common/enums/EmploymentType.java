package com.hrms.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Employment type enum matching Oracle CHECK constraint:
 * CHK_EMP_TYPE CHECK (EMPLOYMENT_TYPE IN (
 *   'FULL_TIME','PART_TIME','CONTRACT','INTERN','CONSULTANT'
 * ))
 */
public enum EmploymentType {

    FULL_TIME("Full Time"),
    PART_TIME("Part Time"),
    CONTRACT("Contract"),
    INTERN("Intern"),
    CONSULTANT("Consultant");

    private final String displayName;

    EmploymentType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return this.name();
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static EmploymentType fromValue(String value) {
        for (EmploymentType et : EmploymentType.values()) {
            if (et.name().equalsIgnoreCase(value)) {
                return et;
            }
        }
        throw new IllegalArgumentException(
            "Invalid employment type: '" + value
                + "'. Accepted values: FULL_TIME, PART_TIME, CONTRACT, INTERN, CONSULTANT"
        );
    }
}
