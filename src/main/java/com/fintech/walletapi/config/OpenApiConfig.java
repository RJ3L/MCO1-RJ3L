package com.fintech.walletapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GMoney E-Wallet API",
                version = "1.0.0",
                description = "Peso and forex wallets, transfers with automatic currency conversion, "
                        + "an immutable ledger and live ECB exchange rates (via Frankfurter)."),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT returned by POST /api/v1/auth/login (valid for 1 hour)")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
