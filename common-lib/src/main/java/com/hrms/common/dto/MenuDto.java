package com.hrms.common.dto;

import lombok.Data;
import java.util.List;

@Data
public class MenuDto {
    private Long        mainMenuId;
    private String      mainMenuName;
    private String      icon;
    private Integer     sortOrder;
    private String      route;          // null if has children
    private List<SubMenuDto> children;

    @Data
    public static class SubMenuDto {
        private Long    subMenuId;
        private String  subMenuCode;
        private String  subMenuName;
        private String  subMenuAction; // route
        private Integer subMenuType;
        private Integer sortOrder;
        private Boolean canView;
        private Boolean canCreate;
        private Boolean canEdit;
        private Boolean canDelete;
    }
}