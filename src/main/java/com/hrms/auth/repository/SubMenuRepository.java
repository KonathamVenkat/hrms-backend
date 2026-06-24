package com.hrms.auth.repository;
import com.hrms.auth.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface SubMenuRepository extends JpaRepository<SubMenu, Long> {
    List<SubMenu> findByMainMenuIdAndIsActiveOrderBySortOrder(
        Long mainMenuId, Integer isActive);
}
