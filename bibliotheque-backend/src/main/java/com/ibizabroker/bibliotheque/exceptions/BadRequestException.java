package com.ibizabroker.bibliotheque.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Requête syntaxiquement recevable mais incomplète ou incohérente : 400.
 *
 * Écrite sur le modèle de NotFoundException. Le @ResponseStatus suffirait à
 * poser le code, mais le corps serait alors vide (server.error.include-message
 * vaut « never » depuis Spring Boot 2.3) : c'est ReservationExceptionHandler
 * qui se charge de renvoyer le message.
 */
@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }
}
