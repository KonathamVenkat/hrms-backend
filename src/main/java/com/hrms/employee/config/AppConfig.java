package com.hrms.employee.config;

import com.hrms.common.audit.AuditorAwareImpl;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
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
                .license(new License().name("Proprietary")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
    

}
