package com.example.security;

import static org.springframework.security.config.Customizer.withDefaults;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtSecurityAutoConfiguration {

    /**
     * Default rules: a JWT is validated when present, nothing requires one. A service that needs authenticated
     * endpoints declares its own SecurityFilterChain (see ms-cart).
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain jwtOptionalSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }

    @Bean
    CustomerContextFilter customerContextFilter(Tracer tracer) {
        return new CustomerContextFilter(tracer);
    }

    /**
     * Spring Security would add one span per filter phase to every trace: keep traces readable.
     */
    @Bean
    SecurityObservationSettings securityObservationSettings() {
        return SecurityObservationSettings.noObservations();
    }
}
