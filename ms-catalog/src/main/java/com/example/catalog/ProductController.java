package com.example.catalog;

import java.math.BigDecimal;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * No observability code here: customer.id and cart.id show up in these logs and spans
 * only because they travel in the baggage.
 */
@RestController
class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @GetMapping("/products/{sku}")
    Product getProduct(@PathVariable String sku) throws InterruptedException {
        log.info("Looking up product {}", sku);
        switch (sku) {
            case "SKU-SLOW" -> {
                log.warn("Pricing engine is slow for {}", sku);
                Thread.sleep(Duration.ofSeconds(2));
            }
            case "SKU-BROKEN" -> {
                IllegalStateException cause = new IllegalStateException("Price list EU-2026 has no entry for " + sku);
                log.error("Cannot compute price for {}", sku, cause);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Pricing failed for " + sku, cause);
            }
            case "SKU-BUG" -> throw new IllegalStateException("Unexpected null tax rate for " + sku); // not handled
            default -> {
            }
        }
        return new Product(sku, "Product " + sku, new BigDecimal("19.90"));
    }

    record Product(String sku, String name, BigDecimal price) {
    }
}
