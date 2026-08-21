package com.ibizabroker.bibliotheque.configuration;

import com.ibizabroker.bibliotheque.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Bonus : fait passer en EXPIREE les réservations dont la validité de 7 jours
 * (RG-04) est écoulée.
 *
 * POURQUOI UNE TÂCHE PLANIFIÉE ET NON UN CALCUL À LA LECTURE
 * Déduire le statut à l'affichage laisserait la base incohérente : une
 * réservation périmée continuerait de compter dans RG-02 et RG-03, et bloquerait
 * l'adhérent. La bascule doit donc être écrite.
 *
 * @EnableScheduling est porté par cette classe plutôt que par
 * BibliothequeApplication : la fonctionnalité appartient au module Réservation,
 * la retirer ne demande que de supprimer ce fichier.
 */
@Configuration
@EnableScheduling
public class ReservationExpirationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    /** Une heure, en millisecondes. */
    private static final long PERIODE = 60L * 60L * 1000L;

    /** Une minute : laisse le contexte finir de démarrer avant le premier balayage. */
    private static final long DELAI_INITIAL = 60L * 1000L;

    private final ReservationService reservationService;

    public ReservationExpirationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * fixedDelay et non fixedRate : le prochain balayage part de la fin du
     * précédent, deux exécutions ne peuvent donc pas se chevaucher.
     */
    @Scheduled(initialDelay = DELAI_INITIAL, fixedDelay = PERIODE)
    public void expirerLesReservationsEchues() {
        int expirees = reservationService.expirerLesReservationsEchues();
        if (expirees > 0) {
            LOGGER.info("[reservations] {} reservation(s) basculee(s) en EXPIREE", expirees);
        }
    }
}
