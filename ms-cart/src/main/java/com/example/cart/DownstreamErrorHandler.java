package com.example.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Downstream failures are logged once, here: the message says what it costs ms-cart (the cart is not served),
 * the attached exception says which call failed (method, URL, status). The cause is not repeated: the failing
 * service logged it under the same trace_id.
 */
@RestControllerAdvice
class DownstreamErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail unreachable(ResourceAccessException ex) {
        log.error("Cart not served: a downstream service did not answer in time", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "A downstream service did not answer in time");
    }

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail failed(RestClientResponseException ex) {
        log.error("Cart not served: a downstream service answered {}", ex.getStatusCode().value(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "A downstream service failed");
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail unreadable(RestClientException ex) {
        log.error("Cart not served: a downstream call failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "A downstream service failed");
    }
}
