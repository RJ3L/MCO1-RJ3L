package com.fintech.walletapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 description of the GMoney API, served by springdoc at
 * {@code /v3/api-docs} and rendered by Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>Every operation requires the {@value #BEARER_AUTH} scheme by default. In Swagger UI,
 * call {@code POST /api/v1/auth/login}, copy the token from {@code data}, click
 * <b>Authorize</b> and paste it (without the {@code Bearer } prefix).</p>
 */
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

    /** Name of the JWT security scheme in the OpenAPI document. */
    public static final String BEARER_AUTH = "bearerAuth";
}
