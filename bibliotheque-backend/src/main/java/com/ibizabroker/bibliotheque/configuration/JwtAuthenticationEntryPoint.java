package com.ibizabroker.bibliotheque.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizabroker.bibliotheque.exceptions.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * La réponse 401 : « je ne sais pas qui vous êtes ».
 *
 * Spring Security l'appelle quand une requête anonyme atteint une route
 * protégée (RS-01). Le 403, « je sais qui vous êtes mais vous n'avez pas le
 * droit », ne passe jamais par ici : il est produit par
 * ReservationExceptionHandler et par l'AccessDeniedHandler de
 * WebSecurityConfiguration, pour un utilisateur déjà authentifié.
 *
 * Le corps reprend le format ApiError du module Réservation, et le message
 * précise la cause relevée par JwtRequestFilter : token absent, expiré ou
 * illisible. Le client d'un token expiré sait ainsi qu'il doit se reconnecter.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String motif = String.valueOf(request.getAttribute(JwtRequestFilter.ATTRIBUT_MOTIF));
        String message;
        switch (motif) {
            case JwtRequestFilter.MOTIF_EXPIRE:
                message = "Le jeton a expiré : reconnectez-vous via POST /authenticate.";
                break;
            case JwtRequestFilter.MOTIF_INVALIDE:
                message = "Le jeton est invalide : signature ou format incorrect.";
                break;
            default:
                message = "Authentification requise : fournissez un jeton dans l'en-tête "
                        + "« Authorization: Bearer <token> ».";
        }

        LOGGER.warn("[securite] 401 {} {} (jeton {})", request.getMethod(), request.getRequestURI(), motif);

        ApiError corps = new ApiError(HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(), message);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), corps);
    }

}
