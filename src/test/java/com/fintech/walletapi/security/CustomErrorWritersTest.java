package com.fintech.walletapi.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class CustomErrorWritersTest {

    private final CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(JsonMapper.builder().build());
    private final CustomAccessDeniedHandler deniedHandler = new CustomAccessDeniedHandler(JsonMapper.builder().build());

    @Test
    @DisplayName("TC-AEP-01 | CustomAuthenticationEntryPoint.commence | unauthenticated request -> status 401")
    void commence_status() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("TC-AEP-02 | CustomAuthenticationEntryPoint.commence | content type is application/json")
    void commence_contentType() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));
        assertTrue(response.getContentType().startsWith("application/json"));
    }

    @Test
    @DisplayName("TC-AEP-03 | CustomAuthenticationEntryPoint.commence | body carries the standard 401 message")
    void commence_body() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));
        assertTrue(response.getContentAsString().contains("Access denied: No token or invalid token provided"));
    }

    @Test
    @DisplayName("TC-ADH-01 | CustomAccessDeniedHandler.handle | denied request -> status 403")
    void handle_status() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        deniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("TC-ADH-02 | CustomAccessDeniedHandler.handle | content type is application/json")
    void handle_contentType() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        deniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));
        assertTrue(response.getContentType().startsWith("application/json"));
    }

    @Test
    @DisplayName("TC-ADH-03 | CustomAccessDeniedHandler.handle | body carries 'Forbidden' and the permission message")
    void handle_body() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        deniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));
        String body = response.getContentAsString();
        assertTrue(body.contains("Forbidden"));
        assertTrue(body.contains("You do not have permission to access this resource"));
    }
}
