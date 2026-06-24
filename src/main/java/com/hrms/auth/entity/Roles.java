package com.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ROLES")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Roles {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "roles_seq")
    @SequenceGenerator(name = "roles_seq", sequenceName = "ROLES_SEQ", allocationSize = 1)
    @Column(name = "ROLE_ID")
    private Long roleId;

    @Column(name = "ROLE_NAME", nullable = false, unique = true, length = 50)
    private String roleName;

    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    @Column(name = "IS_ACTIVE")
    private Integer isActive;
}