package com.hrms.common.enums;

public enum UserRole {
    HR_ADMIN,
    HR_MANAGER,
    EMPLOYEE,
    SYSTEM_ADMIN,
    GENERAL_MANAGER,
    CEO;

    public String asAuthority() {
        return "ROLE_" + this.name();
    }
}
