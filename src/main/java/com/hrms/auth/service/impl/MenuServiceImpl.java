package com.hrms.auth.service.impl;

import com.hrms.auth.entity.MainMenu;
import com.hrms.auth.entity.RoleRights;
import com.hrms.auth.entity.Roles;               // ✅ Roles not Role
import com.hrms.auth.entity.SubMenu;
import com.hrms.auth.repository.MainMenuRepository;
import com.hrms.auth.repository.RolesRepository;
import com.hrms.auth.repository.RoleRightsRepository;
import com.hrms.auth.repository.RolesRepository;
import com.hrms.auth.repository.SubMenuRepository;
import com.hrms.auth.service.MenuService;
import com.hrms.common.dto.MenuDto;
import com.hrms.common.dto.MenuDto.SubMenuDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuServiceImpl implements MenuService {

    private final RolesRepository       rolesRepository;
    private final MainMenuRepository   mainMenuRepository;
    private final SubMenuRepository    subMenuRepository;
    private final RoleRightsRepository roleRightsRepository;

    @Override
    public List<MenuDto> getMenuForUser(String roleName) {
        log.debug("Building sidebar menu for role: {}", roleName);

        Optional<Roles> roleOpt = rolesRepository.findByRoleName(roleName);
        if (roleOpt.isEmpty()) {
            // An account whose role has no ROLES row gets an empty menu rather than a 500
            log.warn("No ROLES row for role '{}' — returning an empty menu", roleName);
            return new ArrayList<>();
        }
        Roles role = roleOpt.get();
        List<MainMenu> allActiveMenus = mainMenuRepository
                .findByIsActiveOrderBySortOrder(1);

        List<MenuDto> result = new ArrayList<>();
        for (MainMenu mainMenu : allActiveMenus) {
            buildMenuDto(role, mainMenu).ifPresent(result::add);
        }

        log.debug("Menu built for role [{}] — {} items", roleName, result.size());
        return result;
    }

    private Optional<MenuDto> buildMenuDto(Roles role, MainMenu mainMenu) {  // ✅ Roles
        List<RoleRights> mainRights = roleRightsRepository
                .findByRoleIdAndMainMenuId(role.getRoleId(), mainMenu.getMainMenuId());

        if (mainRights.isEmpty()) return Optional.empty();

        MenuDto dto = mapToMenuDto(mainMenu);
        List<SubMenuDto> children = buildSubMenuDtos(role, mainMenu);

        if (mainMenu.getRoute() == null && children.isEmpty()) return Optional.empty();
        if (!children.isEmpty()) dto.setChildren(children);

        return Optional.of(dto);
    }

    private List<SubMenuDto> buildSubMenuDtos(Roles role, MainMenu mainMenu) {  // ✅ Roles
        List<RoleRights> subRights = roleRightsRepository
                .findSubMenuRightsByRole(role.getRoleId(), mainMenu.getMainMenuId());

        List<SubMenuDto> children = new ArrayList<>();
        for (RoleRights right : subRights) {
            if (right.getMenuId() == null) continue;
            subMenuRepository.findById(right.getMenuId()).ifPresent(sub -> {
                if (sub.getIsActive() != null && sub.getIsActive() == 1) {
                    children.add(mapToSubMenuDto(sub, right));
                }
            });
        }

        children.sort(Comparator.comparingInt(SubMenuDto::getSortOrder));
        return children;
    }

    private MenuDto mapToMenuDto(MainMenu mainMenu) {
        MenuDto dto = new MenuDto();
        dto.setMainMenuId(mainMenu.getMainMenuId());
        dto.setMainMenuName(mainMenu.getMainMenuName());
        dto.setIcon(mainMenu.getIcon());
        dto.setSortOrder(mainMenu.getSortOrder());
        dto.setRoute(mainMenu.getRoute());
        dto.setChildren(new ArrayList<>());
        return dto;
    }

    private SubMenuDto mapToSubMenuDto(SubMenu sub, RoleRights right) {
        SubMenuDto dto = new SubMenuDto();
        dto.setSubMenuId(sub.getSubMenuId());
        dto.setSubMenuCode(sub.getSubMenuCode());
        dto.setSubMenuName(sub.getSubMenuName());
        dto.setSubMenuAction(sub.getSubMenuAction());
        dto.setSubMenuType(sub.getSubMenuType());
        dto.setSortOrder(sub.getSortOrder() != null ? sub.getSortOrder() : 0);
        dto.setCanView(right.getCanView()     != null && right.getCanView()   == 1);
        dto.setCanCreate(right.getCanCreate() != null && right.getCanCreate() == 1);
        dto.setCanEdit(right.getCanEdit()     != null && right.getCanEdit()   == 1);
        dto.setCanDelete(right.getCanDelete() != null && right.getCanDelete() == 1);
        return dto;
    }
}