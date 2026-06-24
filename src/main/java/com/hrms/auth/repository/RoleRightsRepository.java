package com.hrms.auth.repository;
import com.hrms.auth.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RoleRightsRepository extends JpaRepository<RoleRights, Long> {

    // Get all main menus accessible by role
    @Query("""
        SELECT rr FROM RoleRights rr
        WHERE rr.roleId = :roleId
        AND rr.mainMenuId = :mainMenuId
        AND rr.canView = 1
        """)
    List<RoleRights> findByRoleIdAndMainMenuId(
        @Param("roleId")     Long roleId,
        @Param("mainMenuId") Long mainMenuId);

    // Get sub menus accessible by role under a main menu
    @Query("""
        SELECT rr FROM RoleRights rr
        WHERE rr.roleId     = :roleId
        AND   rr.mainMenuId = :mainMenuId
        AND   rr.menuId     IS NOT NULL
        AND   rr.canView    = 1
        """)
    List<RoleRights> findSubMenuRightsByRole(
        @Param("roleId")     Long roleId,
        @Param("mainMenuId") Long mainMenuId);

    List<RoleRights> findByRoleIdAndMenuId(Long roleId, Long menuId);
}