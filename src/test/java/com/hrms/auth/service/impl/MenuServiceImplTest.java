package com.hrms.auth.service.impl;

import com.hrms.auth.entity.MainMenu;
import com.hrms.auth.entity.RoleRights;
import com.hrms.auth.entity.Roles;
import com.hrms.auth.entity.SubMenu;
import com.hrms.auth.repository.MainMenuRepository;
import com.hrms.auth.repository.RoleRightsRepository;
import com.hrms.auth.repository.RolesRepository;
import com.hrms.auth.repository.SubMenuRepository;
import com.hrms.common.dto.MenuDto;
import com.hrms.employee.service.OrganisationLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** EMPLOYEE logins see My Approvals / Requisitions only while holding the post; other roles are unaffected. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuServiceImplTest {

    private static final long ROLE_ID = 3L;
    private static final long ME = 42L;
    private static final long APPROVALS = 20L;
    private static final long RECRUITMENT = 18L;
    private static final long LEAVE = 10L;

    @Mock RolesRepository rolesRepository;
    @Mock MainMenuRepository mainMenuRepository;
    @Mock SubMenuRepository subMenuRepository;
    @Mock RoleRightsRepository roleRightsRepository;
    @Mock OrganisationLookup organisation;
    @InjectMocks MenuServiceImpl service;

    @BeforeEach
    void menus() {
        when(rolesRepository.findByRoleName("EMPLOYEE")).thenReturn(Optional.of(role("EMPLOYEE")));
        when(rolesRepository.findByRoleName("HR_ADMIN")).thenReturn(Optional.of(role("HR_ADMIN")));
        when(mainMenuRepository.findByIsActiveOrderBySortOrder(1)).thenReturn(List.of(
            main(LEAVE, "Leave"), main(RECRUITMENT, "Recruitment"), main(APPROVALS, "Approvals")));
        item(LEAVE, 101L, "LEAVE_REQUEST");
        item(RECRUITMENT, 181L, MenuServiceImpl.REC_REQUISITIONS);
        item(APPROVALS, 201L, MenuServiceImpl.WORKFLOW_INBOX);
    }

    @Test
    void plainEmployeeSeesNeitherPostItem() {
        assertEquals(List.of("Leave"), names(service.getMenuForUser("EMPLOYEE", ME)));
    }

    @Test
    void branchManagerOrSecretarySeesApprovalsOnly() {
        when(organisation.holdsApproverPost(ME)).thenReturn(true);
        assertEquals(List.of("Leave", "Approvals"), names(service.getMenuForUser("EMPLOYEE", ME)));
    }

    @Test
    void departmentHeadSeesBoth() {
        when(organisation.holdsApproverPost(ME)).thenReturn(true);
        when(organisation.headsActiveDepartment(ME)).thenReturn(true);
        assertEquals(List.of("Leave", "Recruitment", "Approvals"), names(service.getMenuForUser("EMPLOYEE", ME)));
    }

    @Test
    void employeeLoginWithoutEmployeeRecordSeesNeither() {
        assertEquals(List.of("Leave"), names(service.getMenuForUser("EMPLOYEE", null)));
        verify(organisation, never()).holdsApproverPost(anyLong());
    }

    @Test
    void otherRolesKeepTheirRightsWithoutPostChecks() {
        assertEquals(List.of("Leave", "Recruitment", "Approvals"), names(service.getMenuForUser("HR_ADMIN", ME)));
        verify(organisation, never()).holdsApproverPost(anyLong());
        verify(organisation, never()).headsActiveDepartment(anyLong());
    }

    private static Roles role(String name) {
        return Roles.builder().roleId(ROLE_ID).roleName(name).isActive(1).build();
    }

    private static MainMenu main(long id, String name) {
        return new MainMenu(id, name, "icon", (int) id, null, 1);
    }

    private void item(long mainId, long subId, String code) {
        RoleRights right = new RoleRights(subId, ROLE_ID, subId, mainId, 1, 0, 0, 0);
        when(roleRightsRepository.findByRoleIdAndMainMenuId(ROLE_ID, mainId)).thenReturn(List.of(right));
        when(roleRightsRepository.findSubMenuRightsByRole(ROLE_ID, mainId)).thenReturn(List.of(right));
        when(subMenuRepository.findById(subId))
            .thenReturn(Optional.of(new SubMenu(subId, code, code, "/app/x", 1, mainId, 1, 1)));
    }

    private static List<String> names(List<MenuDto> menu) {
        return menu.stream().map(MenuDto::getMainMenuName).toList();
    }
}
