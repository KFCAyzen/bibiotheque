package com.ibizabroker.bibliotheque.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Règle de gestion enfreinte : 409.
 *
 * La demande est bien formée et vise des ressources existantes ; c'est l'état
 * du domaine qui la refuse. D'où le 409 plutôt qu'un 400, et surtout plutôt
 * que le 500 que produirait une exception non traitée.
 *
 * Le message porte toujours la référence de la règle (RG-01 à RG-06).
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
