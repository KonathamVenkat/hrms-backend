package com.hrms.common.enums;

public enum UserRole {
    HR_ADMIN,
    HR_MANAGER,
    EMPLOYEE,
    SYSTEM_ADMIN;

    public String asAuthority() {
        return "ROLE_" + this.name();
    }
}
