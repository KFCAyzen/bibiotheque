package com.ibizabroker.bibliotheque.exceptions;

import com.ibizabroker.bibliotheque.controller.ReservationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduction des exceptions du module Réservation en réponses HTTP.
 *
 * PORTÉE VOLONTAIREMENT RESTREINTE
 * « assignableTypes = ReservationController.class » : ce conseil ne s'applique
 * qu'aux routes /api/reservations. Un @RestControllerAdvice global aurait
 * changé, au passage, le corps des réponses d'erreur des contrôleurs existants
 * — donc le contrat que consomme déjà le front Angular. Le module nouveau
 * n'impose rien à l'ancien.
 */
@RestControllerAdvice(assignableTypes = ReservationController.class)
public class ReservationExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationExceptionHandler.class);

    /**
     * Refus prononcé par le service : la réservation appartient à un autre
     * adhérent (RS-03, RS-05), ou le demandeur agit au nom d'un autre (RS-04).
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> accesRefuse(ForbiddenException exception) {
        journaliserRefus(exception.getMessage());
        return reponse(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    /**
     * Refus prononcé par un @PreAuthorize du contrôleur : le rôle ne suffit
     * pas (RS-02). L'exception de Spring Security ne porte qu'un « Access is
     * denied » ; on lui substitue un message qui nomme le rôle attendu.
     *
     * Ce cas n'arrive jamais pour un anonyme : la chaîne de filtres l'a déjà
     * arrêté en 401 avant qu'un contrôleur ne soit invoqué.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> roleInsuffisant(AccessDeniedException exception) {
        String message = "RS-02 : cette action est réservée au rôle BIBLIOTHECAIRE.";
        journaliserRefus(message);
        return reponse(HttpStatus.FORBIDDEN, message);
    }

    /** Identifiant inconnu : 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> introuvable(NotFoundException exception) {
        return reponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /** Champ obligatoire absent ou valeur inexploitable : 400. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> requeteInvalide(BadRequestException exception) {
        return reponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** Règle de gestion enfreinte : 409, avec la référence de la règle. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> regleEnfreinte(ConflictException exception) {
        return reponse(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * JSON illisible, ou corps absent sur un POST.
     * Sans ce traitement, Spring renverrait bien un 400, mais muet.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> corpsIllisible(HttpMessageNotReadableException exception) {
        return reponse(HttpStatus.BAD_REQUEST,
                "Le corps de la requête est absent ou n'est pas un JSON valide. "
                        + "Attendu : { \"livreId\": 1, \"adherentId\": 2 }.");
    }

    /** {id} non numérique dans l'URL, par exemple /api/reservations/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> parametreMalType(MethodArgumentTypeMismatchException exception) {
        return reponse(HttpStatus.BAD_REQUEST,
                "Le paramètre « " + exception.getName() + " » attend un entier.");
    }

    /** Bonus : chaque tentative refusée laisse une trace, avec l'identité du demandeur. */
    private void journaliserRefus(String message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String demandeur = authentication == null ? "anonyme" : authentication.getName();
        LOGGER.warn("[securite] 403 refuse a {} : {}", demandeur, message);
    }

    private ResponseEntity<ApiError> reponse(HttpStatus statut, String message) {
        return ResponseEntity.status(statut)
                .body(new ApiError(statut.value(), statut.getReasonPhrase(), message));
    }
}
