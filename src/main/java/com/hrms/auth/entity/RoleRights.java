package com.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "ROLE_RIGHTS")
@Data @NoArgsConstructor @AllArgsConstructor
public class RoleRights {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator  = "role_rights_seq")
    @SequenceGenerator(name           = "role_rights_seq",
                       sequenceName   = "ROLE_RIGHTS_SEQ",
                       allocationSize = 1)
    @Column(name = "ROLE_RIGHTS_ID")
    private Long roleRightsId;

    @Column(name = "ROLE_ID")
    private Long roleId;

    @Column(name = "MENU_ID")       // SUB_MENU_ID — null if main only
    private Long menuId;

    @Column(name = "MAIN_MENU_ID")
    private Long mainMenuId;

    @Column(name = "CAN_VIEW")
    private Integer canView;

    @Column(name = "CAN_CREATE")
    private Integer canCreate;

    @Column(name = "CAN_EDIT")
    private Integer canEdit;

    @Column(name = "CAN_DELETE")
    private Integer canDelete;
}