package com.hrms.employee.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.auth.security.AuthUserStatusFilter;
import com.hrms.common.security.JwtAuthenticationFilter;
import com.hrms.common.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import java.util.Map;

/**
 * Single SecurityConfig for the entire app (employee + auth + leave modules).
 * Uses common-lib JwtAuthenticationFilter — one-param constructor only.
 * auth/config/SecurityConfig.java is DELETED — this replaces it.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider   jwtTokenProvider;
    private final ObjectMapper       objectMapper;
    private final AuthUserRepository authUserRepository;

    @Value("${hrms.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: sign-in, token refresh, logout (a user whose access token has
                // expired must still be able to revoke their refresh token), liveness probe.
                // /validate and /me now require a valid token like everything else.
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/api/v1/auth/health"
                ).permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("HR_ADMIN")
                // Only the bare health status is public; every other actuator endpoint
                // (info, metrics, ...) is HR_ADMIN-only.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("HR_ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    objectMapper.writeValue(res.getWriter(),
                        Map.of("success", false, "message", "Authentication required.", "statusCode", 401));
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    objectMapper.writeValue(res.getWriter(),
                        Map.of("success", false, "message", "Access denied.", "statusCode", 403));
                })
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider), // one param — matches common-lib
                UsernamePasswordAuthenticationFilter.class
            )
            // Must run after the JWT filter: re-checks account active / role / must-change-password
            .addFilterAfter(
                new AuthUserStatusFilter(authUserRepository, objectMapper),
                JwtAuthenticationFilter.class
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parseOrigins(allowedOrigins));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","X-Requested-With","X-Correlation-ID"));
        config.setExposedHeaders(List.of("X-Total-Count","X-Correlation-ID"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Splits the comma-separated origin list, ignoring spaces around entries and empty entries. */
    static List<String> parseOrigins(String origins) {
        return java.util.Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
