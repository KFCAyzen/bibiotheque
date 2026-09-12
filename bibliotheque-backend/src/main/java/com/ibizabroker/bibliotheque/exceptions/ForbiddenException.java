package com.ibizabroker.bibliotheque.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Accès refusé à un utilisateur authentifié : 403.
 *
 * On sait qui parle — le token l'a établi — mais la réservation visée n'est
 * pas la sienne (RS-03, RS-05) ou il tente d'agir au nom d'un autre (RS-04).
 * À ne pas confondre avec le 401, réservé à l'anonyme, que produit
 * JwtAuthenticationEntryPoint.
 *
 * Levée par ReservationService, qui seul connaît le propriétaire d'une
 * réservation ; les refus qui ne dépendent que du rôle (RS-02) passent par
 * @PreAuthorize et n'ont pas besoin de cette classe.
 *
 * Le message porte la référence de la règle enfreinte (RS-03 à RS-05).
 */
@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
