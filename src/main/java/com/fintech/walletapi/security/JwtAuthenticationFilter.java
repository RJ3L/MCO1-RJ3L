package com.fintech.walletapi.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header on every request and,
 * if the token is valid, marks the request as authenticated.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Creates the filter.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param jwtUtil            token helper
     * @param userDetailsService loads the user named in the token
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Authenticates the request from its Bearer token, then continues the chain.
     *
     * <p><b>Pre-condition:</b> none (requests without a token are allowed through unauthenticated).</p>
     * <p><b>Post-condition:</b> if the token is valid the {@code SecurityContext} holds the user;
     * if it is missing or invalid the context is empty and Spring Security decides (usually 401).
     * The rest of the filter chain always runs.</p>
     *
     * @param request     the incoming request
     * @param response    the outgoing response
     * @param filterChain the remaining filters
     * @throws ServletException if a later filter fails
     * @throws IOException      if a later filter fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        // No Bearer token: continue as anonymous; protected endpoints will then answer 401
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Strip the "Bearer " prefix
        final String jwt = authHeader.substring(7);
        try {
            // Parsing verifies the signature; only authenticate when nobody is authenticated yet
            final String username = jwtUtil.extractUsername(jwt);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                // Token belongs to this user and has not expired: record the identity and roles for this request
                if (jwtUtil.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // Forged, expired or malformed token, or a deleted user: forget any identity so the request
            // is treated exactly like one without a token
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
