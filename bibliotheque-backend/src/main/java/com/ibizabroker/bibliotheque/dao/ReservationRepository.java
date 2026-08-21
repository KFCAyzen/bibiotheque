package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.StatutReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Accès aux réservations.
 *
 * Les noms de méthodes suivent la dérivation de requêtes de Spring Data : le
 * souligné (« Adherent_UserId ») lève l'ambiguïté entre la traversée d'une
 * relation et un nom de propriété composé.
 *
 * Les trois méthodes de comptage/existence renvoient une agrégation plutôt
 * qu'une liste : RG-02 et RG-03 n'ont pas besoin de charger les entités.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    List<Reservation> findByStatut(StatutReservation statut);

    List<Reservation> findByAdherent_UserId(Integer userId);

    List<Reservation> findByAdherent_UserIdAndStatut(Integer userId, StatutReservation statut);

    /** RG-02 : une seule réservation active par couple (adhérent, livre). */
    boolean existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(Integer bookId,
                                                              Integer userId,
                                                              Collection<StatutReservation> statuts);

    /** RG-03 : plafond de réservations actives simultanées. */
    long countByAdherent_UserIdAndStatutIn(Integer userId, Collection<StatutReservation> statuts);

    /** Balayage des réservations actives dont la date de validité est dépassée. */
    List<Reservation> findByStatutInAndDateExpirationBefore(Collection<StatutReservation> statuts,
                                                            LocalDateTime instant);
}
