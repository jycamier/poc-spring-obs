package com.example.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Last resolver: an exception nobody handled would otherwise reach Tomcat, which logs it once the request has left
 * the trace scope, so without trace_id, customer.id or cart.id. Logged here, still inside the scope, then answered
 * with a 500 (rendered by Spring Boot's /error).
 */
public class UnhandledExceptionResolver implements HandlerExceptionResolver, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UnhandledExceptionResolver.class);

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
                                         Exception ex) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        try {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (IOException sendFailure) {
            return null;
        }
        return new ModelAndView();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
