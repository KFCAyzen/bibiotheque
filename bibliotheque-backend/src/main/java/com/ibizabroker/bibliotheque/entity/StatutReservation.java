package com.ibizabroker.bibliotheque.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cycle de vie d'une réservation.
 *
 * EN_ATTENTE et DISPONIBLE sont les deux statuts dits « actifs » : ce sont eux
 * que comptent RG-02 et RG-03, et les seuls depuis lesquels une annulation est
 * possible (RG-05).
 *
 * ANNULEE, EXPIREE et HONOREE sont des statuts définitifs : plus aucune
 * transition n'en part (RG-06).
 */
public enum StatutReservation {

    /** Le livre est encore emprunté : l'adhérent attend son tour. */
    EN_ATTENTE(true),

    /** Un exemplaire est revenu : l'adhérent est prévenu et peut venir le chercher. */
    DISPONIBLE(true),

    /** Annulée par l'adhérent ou par la bibliothèque. */
    ANNULEE(false),

    /** Les 7 jours de validité (RG-04) sont écoulés. */
    EXPIREE(false),

    /** L'adhérent a effectivement emprunté le livre réservé. */
    HONOREE(false);

    private final boolean actif;

    StatutReservation(boolean actif) {
        this.actif = actif;
    }

    /** Une réservation est active si son statut est EN_ATTENTE ou DISPONIBLE. */
    public boolean estActif() {
        return actif;
    }

    /** Un statut définitif ne peut plus changer : c'est exactement RG-06. */
    public boolean estDefinitif() {
        return !actif;
    }

    /** Les statuts considérés comme actifs, pour les requêtes « ... In(...) ». */
    public static List<StatutReservation> statutsActifs() {
        return Collections.unmodifiableList(
                Arrays.stream(values())
                        .filter(StatutReservation::estActif)
                        .collect(Collectors.toList())
        );
    }

    /** Liste lisible des valeurs acceptées, utilisée dans les messages d'erreur 400. */
    public static String valeursAcceptees() {
        return Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
