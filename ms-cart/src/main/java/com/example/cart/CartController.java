package com.example.cart;

import com.example.cart.CatalogClient.Product;
import com.example.cart.UserClient.Customer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private static final Map<String, List<String>> CARTS = Map.of(
        "cart-42", List.of("SKU-1", "SKU-2"),
        "cart-broken", List.of("SKU-1", "SKU-BROKEN"),
        "cart-slow", List.of("SKU-1", "SKU-SLOW"),
        "cart-bug", List.of("SKU-1", "SKU-BUG"));

    // HTTP service clients are built on Spring Boot's RestClient: every call is observed
    // (span + traceparent + baggage headers)
    private final UserClient users;
    private final CatalogClient catalog;

    CartController(UserClient users, CatalogClient catalog) {
        this.users = users;
        this.catalog = catalog;
    }

    // customer.id and cart.id are already in the baggage (CustomerContextFilter, CartContextInterceptor):
    // they show up in every log and span from here on, here and downstream
    @GetMapping("/carts/{cartId}")
    CartView getCart(@PathVariable String cartId, Authentication authentication) {
        log.info("Loading cart");
        List<String> skus = CARTS.get(cartId);
        if (skus == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown cart " + cartId);
        }

        Customer customer = users.getCustomer(authentication.getName());
        List<Product> items = skus.stream().map(catalog::getProduct).toList();
        BigDecimal total = items.stream().map(Product::price).reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Cart loaded: {} items, total {}", items.size(), total);
        return new CartView(cartId, customer, items, total);
    }

    record CartView(String cartId, Customer customer, List<Product> items, BigDecimal total) {
    }
}
