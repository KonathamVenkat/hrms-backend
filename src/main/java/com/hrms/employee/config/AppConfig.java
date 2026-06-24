package com.hrms.employee.config;

import com.hrms.common.audit.AuditorAwareImpl;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableScheduling
public class AppConfig {

    @Value("${spring.application.name:hrms-service}")
    private String applicationName;

    @Bean
    public AuditorAwareImpl auditorAwareImpl() { return new AuditorAwareImpl(); }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("HRMS API")
                .description("Employee, Auth and Leave modules")
                .version("v1.0.0")
                .contact(new Contact().name("HRMS Team").email("hrms-dev@company.com"))
                .license(new License().name("Proprietary")))
            .servers(List.of(
                new Server().url("http://localhost:8082").description("Local"),
                new Server().url("https://hrms-api.company.com").description("Production")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
    

}
