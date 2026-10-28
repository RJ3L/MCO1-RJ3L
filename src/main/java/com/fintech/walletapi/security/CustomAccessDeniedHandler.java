package com.fintech.walletapi.security;

import com.fintech.walletapi.exception.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes a JSON 403 body when the security filter chain denies access
 * (the user is logged in but lacks the required role).
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * Creates the handler.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param objectMapper Jackson mapper used to serialize the error body
     */
    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the 403 response.
     *
     * <p><b>Pre-condition:</b> an authenticated request was denied by the filter chain.</p>
     * <p><b>Post-condition:</b> the response has status 403, type {@code application/json}
     * and an {@link ErrorResponseDTO} body.</p>
     *
     * @param request               the denied request
     * @param response              the response to write to
     * @param accessDeniedException the reason for the denial
     * @throws IOException if the body cannot be written
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        ErrorResponseDTO error = new ErrorResponseDTO(
                403, "Forbidden", "You do not have permission to access this resource");
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
