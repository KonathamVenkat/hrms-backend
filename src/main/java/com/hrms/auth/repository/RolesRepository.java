package com.hrms.auth.repository;

import com.hrms.auth.entity.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RolesRepository extends JpaRepository<Roles, Long> {
    Optional<Roles> findByRoleName(String roleName);
    Optional<Roles> findByRoleNameAndIsActive(String roleName, Integer isActive);
}