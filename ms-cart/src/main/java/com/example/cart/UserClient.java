package com.example.cart;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative client for ms-user. Base URL and timeouts: spring.http.serviceclient.ms-user.* in application.yml.
 */
@HttpExchange
public interface UserClient {

    @GetExchange("/users/{id}")
    Customer getCustomer(@PathVariable String id);

    record Customer(String id, String name, String tier) {
    }
}
