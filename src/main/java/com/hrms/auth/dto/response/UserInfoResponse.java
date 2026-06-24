package com.hrms.auth.dto.response;

import com.hrms.common.enums.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Matches Angular frontend:
 *   export interface UserInfo {
 *     id: number; username: string; email: string; fullName: string;
 *     role: UserRole; department?: string; employeeId?: string;
 *     avatarUrl?: string; lastLogin?: string;
 *   }
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user information")
public class UserInfoResponse {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "john.doe")
    private String username;

    @Schema(example = "john.doe@company.com")
    private String email;

    @Schema(example = "John Doe")
    private String fullName;

    @Schema(example = "HR_ADMIN")
    private UserRole role;

    @Schema(example = "Engineering")
    private String department;

    /** Maps to Angular's `employeeId` — we store the code like EMP-2024-001. */
    @Schema(example = "EMP-2024-001")
    private String employeeId;

    @Schema(example = "https://cdn.company.com/avatars/john-doe.jpg")
    private String avatarUrl;

    @Schema(example = "2026-04-19T08:45:00")
    private String lastLogin;
}
