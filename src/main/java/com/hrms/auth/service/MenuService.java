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
     * @param roleName  e.g. "HR_ADMIN", "MANAGER", "EMPLOYEE"
     * @return          ordered list of MenuDto with nested children
     */
    List<MenuDto> getMenuForUser(String roleName);
}
