package com.example.cart;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative client for ms-catalog. Base URL and timeouts: spring.http.serviceclient.ms-catalog.* in application.yml.
 */
@HttpExchange
public interface CatalogClient {

    @GetExchange("/products/{sku}")
    Product getProduct(@PathVariable String sku);

    record Product(String sku, String name, BigDecimal price) {
    }
}
