package com.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "SUB_MENU")
@Data @NoArgsConstructor @AllArgsConstructor
public class SubMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator  = "sub_menu_seq")
    @SequenceGenerator(name           = "sub_menu_seq",
                       sequenceName   = "SUB_MENU_SEQ",
                       allocationSize = 1)
    @Column(name = "SUB_MENU_ID")
    private Long subMenuId;

    @Column(name = "SUB_MENU_CODE")
    private String subMenuCode;

    @Column(name = "SUB_MENU_NAME")
    private String subMenuName;

    @Column(name = "SUB_MENU_ACTION")
    private String subMenuAction;

    @Column(name = "SUB_MENU_TYPE")
    private Integer subMenuType;

    @Column(name = "MAIN_MENU_ID")
    private Long mainMenuId;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "IS_ACTIVE")
    private Integer isActive;
}