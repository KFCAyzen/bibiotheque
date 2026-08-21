package com.ibizabroker.bibliotheque.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Réservation d'un livre indisponible par un adhérent.
 *
 * Cette entité ne sort jamais du service : le contrôleur ne manipule que
 * ReservationRequestDTO en entrée et ReservationResponseDTO en sortie.
 *
 * Les deux relations sont EAGER : chaque conversion vers le DTO de sortie lit
 * le titre du livre et le nom de l'adhérent. Un chargement paresseux n'aurait
 * donc rien économisé, tout en exposant au LazyInitializationException.
 */
@Data
@Entity
@Table(name = "Reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer reservationId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Books livre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users adherent;

    /**
     * Horodatage posé par le serveur au moment de la création : le client ne
     * fournit jamais cette valeur.
     * updatable = false : même un save() ultérieur ne peut plus la déplacer.
     */
    @Column(name = "date_reservation", nullable = false, updatable = false)
    private LocalDateTime dateReservation;

    /** RG-04 : dateReservation + 7 jours, calculée dans ReservationService. */
    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    /**
     * EnumType.STRING et non ORDINAL : la base garde « EN_ATTENTE » et non « 0 »,
     * ce qui rend la table lisible et survit à un réordonnancement de l'enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReservation statut;

    /** Raccourci de lecture : la réservation est-elle encore active ? */
    public boolean estActive() {
        return statut != null && statut.estActif();
    }
}
