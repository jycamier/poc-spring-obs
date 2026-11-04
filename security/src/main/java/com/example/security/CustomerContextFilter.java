package com.example.security;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the authenticated customer in the baggage. Runs after the Spring Security filter chain, so the JWT has
 * been validated: the customer comes from its claim, never from a header the client could forge.
 * Internal calls carry no token: the customer.id received in the baggage is kept as is.
 */
public class CustomerContextFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public CustomerContextFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        // getName() is the customer_id claim (spring.security.oauth2.resourceserver.jwt.principal-claim-name)
        try (BaggageInScope customer = tracer.createBaggageInScope("customer.id", authentication.getName())) {
            chain.doFilter(request, response);
        }
    }
}
