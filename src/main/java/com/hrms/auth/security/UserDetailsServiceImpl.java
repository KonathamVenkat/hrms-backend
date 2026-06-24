package com.hrms.auth.security;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bridges Spring Security's UserDetailsService with our Oracle AUTH_USERS table.
 * Also exposes loadAuthUser() for the AuthService business layer.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AuthUserRepository authUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        AuthUser user = authUserRepository
            .findByUsernameOrEmail(identifier)
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + identifier));

        return User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(List.of(new SimpleGrantedAuthority(user.getRole().asAuthority())))
            .accountExpired(false)
            .accountLocked(!user.isAccountNonLocked())
            .credentialsExpired(false)
            .disabled(!Boolean.TRUE.equals(user.getIsActive()))
            .build();
    }

    /**
     * Loads the full AuthUser entity — used by AuthServiceImpl after Spring
     * Security authentication to build the JWT response payload.
     */
    @Transactional(readOnly = true)
    public AuthUser loadAuthUser(String identifier) {
        return authUserRepository
            .findByUsernameOrEmail(identifier)
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + identifier));
    }
}
