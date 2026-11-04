package com.example.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/users/{id}")
    User getUser(@PathVariable String id) {
        log.info("Fetching customer profile");
        return new User(id, "Jane Doe", "GOLD");
    }

    record User(String id, String name, String tier) {
    }
}
