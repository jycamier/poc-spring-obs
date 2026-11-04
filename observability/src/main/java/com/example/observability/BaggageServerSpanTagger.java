package com.example.observability;

import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tags the server span with the baggage received from upstream services. The server span is started before that
 * baggage is put in context, so neither BaggageTaggingSpanProcessor nor tag-fields see it.
 * (Baggage created in this service, e.g. cart.id in ms-cart, is tagged by tag-fields when it is created.)
 */
public class BaggageServerSpanTagger extends OncePerRequestFilter {

    private final Tracer tracer;
    private final List<String> fields;

    public BaggageServerSpanTagger(Tracer tracer, List<String> fields) {
        this.tracer = tracer;
        this.fields = fields;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Span span = tracer.currentSpan();
        if (span != null) {
            for (String field : fields) {
                Baggage baggage = tracer.getBaggage(field);
                if (baggage != null && baggage.get() != null) {
                    span.tag(field, baggage.get());
                }
            }
        }
        chain.doFilter(request, response);
    }
}
