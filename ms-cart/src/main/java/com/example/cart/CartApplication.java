package com.example.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.service.registry.ImportHttpServices;

// One group per downstream service: configured by spring.http.serviceclient.<group>.*
@SpringBootApplication
@ImportHttpServices(group = "ms-user", types = UserClient.class)
@ImportHttpServices(group = "ms-catalog", types = CatalogClient.class)
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
