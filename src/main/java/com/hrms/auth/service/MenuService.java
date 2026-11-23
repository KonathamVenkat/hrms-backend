package com.hrms.auth.service;

import com.hrms.common.dto.MenuDto;
import java.util.List;

/**
 * MenuService — defines contract for building role-based sidebar menu.
 * Implementation: MenuServiceImpl
 */
public interface MenuService {

    /**
     * Returns the filtered list of main menus and sub-menus
     * accessible by the given role name.
     *
     * @param roleName    e.g. "HR_ADMIN", "MANAGER", "EMPLOYEE"
     * @param employeeId  the caller's own employee id (null when the login has no employee); an EMPLOYEE login sees
     *                    My Approvals only while holding an approver post and Requisitions only while heading a
     *                    department
     * @return            ordered list of MenuDto with nested children
     */
    List<MenuDto> getMenuForUser(String roleName, Long employeeId);
}
