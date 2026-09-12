package com.ibizabroker.bibliotheque.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizabroker.bibliotheque.exceptions.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * La réponse 403 : « je sais qui vous êtes, mais vous n'avez pas le droit ».
 *
 * Spring Security l'appelle quand un utilisateur authentifié se heurte à une
 * règle d'URL de WebSecurityConfiguration — ici, un ADHERENT qui tente le
 * DELETE réservé au bibliothécaire (RS-02). Les refus prononcés plus loin
 * dans la chaîne, par @PreAuthorize ou par le service, passent quant à eux
 * par ReservationExceptionHandler ; les deux produisent le même corps
 * ApiError, le client n'a pas à savoir d'où vient le refus.
 *
 * Chaque refus est journalisé avec l'identité du demandeur, c'est le bonus
 * « journaliser les tentatives d'accès refusées ».
 */
@Component
public class AccesInterditHandler implements AccessDeniedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccesInterditHandler.class);

    private final ObjectMapper objectMapper;

    public AccesInterditHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String demandeur = authentication == null ? "anonyme" : authentication.getName();

        LOGGER.warn("[securite] 403 {} {} refuse a {}",
                request.getMethod(), request.getRequestURI(), demandeur);

        ApiError corps = new ApiError(HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "RS-02 : cette action est réservée au rôle BIBLIOTHECAIRE.");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), corps);
    }
}
