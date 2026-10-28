package com.fintech.walletapi.security;

import com.fintech.walletapi.exception.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes a JSON 401 body when a protected endpoint is called without a valid JWT.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Creates the entry point.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param objectMapper Jackson mapper used to serialize the error body
     */
    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the 401 response.
     *
     * <p><b>Pre-condition:</b> an unauthenticated request reached a protected endpoint.</p>
     * <p><b>Post-condition:</b> the response has status 401, type {@code application/json}
     * and an {@link ErrorResponseDTO} body.</p>
     *
     * @param request       the rejected request
     * @param response      the response to write to
     * @param authException the reason authentication failed
     * @throws IOException if the body cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        ErrorResponseDTO error = new ErrorResponseDTO(
                401, "Unauthorized", "Access denied: No token or invalid token provided");
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
