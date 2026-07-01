package com.networking.ems.snmp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security is on the classpath, so by default EVERY endpoint is locked
 * behind HTTP Basic with a random generated password. For local development we
 * open the /device/** API so you can test the SNMP calls without auth.
 *
 * TODO (production): replace permitAll() with real authentication/authorization
 *       before exposing this beyond your machine.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                        .requestMatchers("/device/**", "/devices/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
