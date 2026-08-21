package com.ibizabroker.bibliotheque.exceptions;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Corps JSON renvoyé pour toute erreur du module Réservation.
 *
 * Sans lui, un @ResponseStatus seul renverrait le bon code mais un corps sans
 * message : l'appelant lirait « 409 » sans savoir quelle règle il a enfreinte.
 */
@Data
public class ApiError {

    private LocalDateTime horodatage;

    private int statut;

    private String erreur;

    private String message;

    public ApiError(int statut, String erreur, String message) {
        this.horodatage = LocalDateTime.now();
        this.statut = statut;
        this.erreur = erreur;
        this.message = message;
    }
}
