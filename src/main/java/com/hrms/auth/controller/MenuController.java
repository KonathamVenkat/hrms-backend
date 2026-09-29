package com.hrms.auth.controller;

import com.hrms.auth.service.MenuService;
import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.MenuDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // ── GET /api/v1/menu/sidebar ─────────────────────────────
    // Called by Angular sidebar on app load
    // JWT token identifies the user → extracts role → returns menu
    @GetMapping("/sidebar")
    public ResponseEntity<ApiResponse<List<MenuDto>>> getSidebarMenu(
            Authentication auth) {

        // Extract role from JWT (Spring Security puts it in authorities)
        String roleName = auth.getAuthorities().stream()
            .findFirst()
            .map(a -> a.getAuthority().replace("ROLE_", ""))
            .orElse("EMPLOYEE");
        List<MenuDto> menu = menuService.getMenuForUser(roleName);
        return ResponseEntity.ok(
            ApiResponse.<List<MenuDto>>builder()
                .success(true)
                .message("Menu loaded successfully")
                .data(menu)
                .statusCode(200)
                .build()
        );
    }
}