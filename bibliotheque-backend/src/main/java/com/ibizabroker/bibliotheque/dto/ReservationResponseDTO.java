package com.ibizabroker.bibliotheque.dto;

import com.ibizabroker.bibliotheque.entity.StatutReservation;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO de sortie : la seule représentation d'une réservation qui franchisse la
 * frontière du service.
 *
 * Il aplatit les deux relations en identifiant + libellé. Renvoyer l'entité
 * aurait exposé la grappe complète Users -> Set<Role>, mot de passe haché
 * compris — le défaut déjà relevé sur /admin/users lors de la séance 1.
 */
@Data
public class ReservationResponseDTO {

    private Integer id;

    private Integer livreId;

    private String livreTitre;

    private Integer adherentId;

    private String adherentNom;

    /** Sérialisée en ISO-8601 par le module JavaTime que Spring Boot enregistre seul. */
    private LocalDateTime dateReservation;

    private LocalDateTime dateExpiration;

    private StatutReservation statut;
}
