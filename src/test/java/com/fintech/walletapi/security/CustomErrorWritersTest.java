package com.fintech.walletapi.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JSON 401 / 403 writers used by the security filter chain:
 * {@link CustomAuthenticationEntryPoint} and {@link CustomAccessDeniedHandler}.
 */
class CustomErrorWritersTest {

    private final CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(JsonMapper.builder().build());
    private final CustomAccessDeniedHandler deniedHandler = new CustomAccessDeniedHandler(JsonMapper.builder().build());

    /**
     * TC-AEP-01: tests {@code CustomAuthenticationEntryPoint.commence}.
     *
     * <p><b>Pre-condition:</b> Unauthenticated request.</p>
     * <p><b>Post-condition:</b> Status 401.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AEP-01 | CustomAuthenticationEntryPoint.commence | unauthenticated request -> status 401")
    void commence_status() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));
        assertEquals(401, response.getStatus());
    }

    /**
     * TC-AEP-02: tests {@code CustomAuthenticationEntryPoint.commence}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Content type is application/json.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AEP-02 | CustomAuthenticationEntryPoint.commence | content type is application/json")
    void commence_contentType() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));
        assertTrue(response.getContentType().startsWith("application/json"));
    }

    /**
     * TC-AEP-03: tests {@code CustomAuthenticationEntryPoint.commence}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Body carries the standard 401 message.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AEP-03 | CustomAuthenticationEntryPoint.commence | body carries the standard 401 message")
    void commence_body() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));
        assertTrue(response.getContentAsString().contains("Access denied: No token or invalid token provided"));
    }

    /**
     * TC-ADH-01: tests {@code CustomAccessDeniedHandler.handle}.
     *
     * <p><b>Pre-condition:</b> Denied request.</p>
     * <p><b>Post-condition:</b> Status 403.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-ADH-01 | CustomAccessDeniedHandler.handle | denied request -> status 403")
    void handle_status() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        deniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));
        assertEquals(403, response.getStatus());
    }

    /**
     * TC-ADH-02: tests {@code CustomAccessDeniedHandler.handle}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Content type is application/json.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-ADH-02 | CustomAccessDeniedHandler.handle | content type is application/json")
    void handle_contentType() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        deniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));
        assertTrue(response.getContentType().startsWith("application/json"));
    }

    /**
     * TC-ADH-03: tests {@code CustomAccessDeniedHandler.handle}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Body carries 'Forbidden' and the permission message.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
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
