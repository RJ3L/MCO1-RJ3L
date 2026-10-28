package com.fintech.walletapi.security;

import com.fintech.walletapi.entity.User;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    /**
     * Clears the security context after each test.
     *
     * <p><b>Pre-condition:</b> A test has finished.</p>
     * <p><b>Post-condition:</b> No authentication leaks into the next test.</p>
     */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * TC-JWTF-01: tests {@code JwtAuthenticationFilter.doFilterInternal}.
     *
     * <p><b>Pre-condition:</b> No Authorization header.</p>
     * <p><b>Post-condition:</b> Chain continues, nobody authenticated.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-JWTF-01 | JwtAuthenticationFilter.doFilterInternal | no Authorization header -> chain continues, nobody authenticated")
    void noHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtil);
    }

    /**
     * TC-JWTF-02: tests {@code JwtAuthenticationFilter.doFilterInternal}.
     *
     * <p><b>Pre-condition:</b> Valid Bearer token.</p>
     * <p><b>Post-condition:</b> User authenticated with roles.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-JWTF-02 | JwtAuthenticationFilter.doFilterInternal | valid Bearer token -> user authenticated with roles")
    void validToken() throws Exception {
        User alice = new User();
        alice.setUsername("alice");
        when(jwtUtil.extractUsername("good")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(alice);
        when(jwtUtil.isTokenValid("good", alice)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    /**
     * TC-JWTF-03: tests {@code JwtAuthenticationFilter.doFilterInternal}.
     *
     * <p><b>Pre-condition:</b> Malformed token.</p>
     * <p><b>Post-condition:</b> Context cleared, chain continues.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-JWTF-03 | JwtAuthenticationFilter.doFilterInternal | malformed token -> context cleared, chain continues")
    void invalidToken() throws Exception {
        when(jwtUtil.extractUsername("bad")).thenThrow(new MalformedJwtException("bad"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    /**
     * TC-JWTF-04: tests {@code JwtAuthenticationFilter.doFilterInternal}.
     *
     * <p><b>Pre-condition:</b> 'Basic' scheme instead of Bearer.</p>
     * <p><b>Post-condition:</b> Ignored.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-JWTF-04 | JwtAuthenticationFilter.doFilterInternal | 'Basic' scheme instead of Bearer -> ignored")
    void nonBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic YWxpY2U6cHc=");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(jwtUtil, userDetailsService);
        verify(filterChain).doFilter(request, response);
    }
}
