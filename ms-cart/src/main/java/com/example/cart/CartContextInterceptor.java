package com.example.cart;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * ms-cart owns the cart: it puts cart.id in the baggage as soon as the route tells which cart is handled.
 * An interceptor rather than a try-with-resources in the controller: the scope stays open until the request
 * completes, so the logs of the exception handlers (DownstreamErrorHandler...) keep cart_id too.
 */
@Component
class CartContextInterceptor implements HandlerInterceptor {

    private static final String SCOPE_ATTRIBUTE = CartContextInterceptor.class.getName() + ".scope";

    private final Tracer tracer;

    CartContextInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
            && variables.get("cartId") instanceof String cartId) {
            // management.tracing.baggage.tag-fields tags the current (server) span at creation
            request.setAttribute(SCOPE_ATTRIBUTE, tracer.createBaggageInScope("cart.id", cartId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (request.getAttribute(SCOPE_ATTRIBUTE) instanceof BaggageInScope scope) {
            scope.close();
        }
    }
}
