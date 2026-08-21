package com.ibizabroker.bibliotheque.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfiguration {

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";

    // Ajouté avec le module Réservation, pour PATCH /api/reservations/{id}/annuler.
    // Sans cette entrée, le prévol OPTIONS refuse la méthode : l'annulation
    // reste injoignable depuis un navigateur (Angular sur :4200, Swagger UI sur
    // :8081) alors qu'elle répond parfaitement en curl.
    private static final String PATCH = "PATCH";

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedMethods(GET, POST, PUT, PATCH, DELETE)
                        .allowedHeaders("*")
                        .allowedOriginPatterns("*")
                        .allowCredentials(true);
            }
        };
    }
}