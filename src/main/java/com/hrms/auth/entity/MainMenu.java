package com.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "MAIN_MENU")
@Data @NoArgsConstructor @AllArgsConstructor
public class MainMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator  = "main_menu_seq")
    @SequenceGenerator(name           = "main_menu_seq",
                       sequenceName   = "MAIN_MENU_SEQ",
                       allocationSize = 1)
    @Column(name = "MAIN_MENU_ID")
    private Long mainMenuId;

    @Column(name = "MAIN_MENU_NAME")
    private String mainMenuName;

    @Column(name = "ICON")
    private String icon;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "ROUTE")
    private String route;

    @Column(name = "IS_ACTIVE")
    private Integer isActive;
}