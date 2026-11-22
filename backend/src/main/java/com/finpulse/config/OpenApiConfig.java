package com.finpulse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger documentation metadata and bearer JWT security scheme configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finPulseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinPulse API")
                        .version("3.0.0-sprint3")
                        .description("Personal Finance & Expense Dashboard REST API"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
