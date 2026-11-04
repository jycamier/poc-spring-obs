package com.example.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring MVC does not report the exceptions it resolves itself (ResponseStatusException, @ExceptionHandler...)
 * to the server observation: the server span of a 500 is then not in error. As a resolver, this remembers the
 * exception; as a filter, once the final status is known, it reports it when the response is a 5xx
 * (a 4xx is not a server error). The span only turns red through {@link Observation#error}, not at stop time.
 */
public class ServerErrorRecorder extends OncePerRequestFilter implements HandlerExceptionResolver, Ordered {

    private static final String EXCEPTION_ATTRIBUTE = ServerErrorRecorder.class.getName() + ".exception";

    /**
     * As a filter: after ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1), i.e. inside the server observation.
     * As a resolver: before Spring MVC's own resolvers (order 0), which would otherwise resolve the exception first.
     */
    private static final int ORDER = -1;

    private final ObservationRegistry observationRegistry;

    public ServerErrorRecorder(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
                                         Exception ex) {
        request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
        return null; // the regular resolvers still produce the response
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        chain.doFilter(request, response);
        Observation server = observationRegistry.getCurrentObservation();
        if (server != null && response.getStatus() >= 500
            && request.getAttribute(EXCEPTION_ATTRIBUTE) instanceof Throwable ex) {
            server.error(ex);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
