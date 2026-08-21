package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.dto.ReservationRequestDTO;
import com.ibizabroker.bibliotheque.dto.ReservationResponseDTO;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.StatutReservation;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.BadRequestException;
import com.ibizabroker.bibliotheque.exceptions.ConflictException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles de gestion du module Réservation.
 *
 * Tests unitaires au sens strict : aucun contexte Spring, aucune base. Les
 * trois dépôts sont simulés, ce qui laisse le service seul face à ses règles
 * et rend l'exécution instantanée.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Integer LIVRE_ID = 7;
    private static final Integer ADHERENT_ID = 2;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BooksRepository booksRepository;

    @Mock
    private UsersRepository usersRepository;

    private ReservationService service() {
        return new ReservationService(reservationRepository, booksRepository, usersRepository);
    }

    // =========================================================================
    // RG-03 — plafond de trois réservations actives
    // =========================================================================

    @Test
    @DisplayName("RG-03 : la quatrième réservation active est refusée en 409")
    void rg03_refuse_au_dela_de_trois_reservations_actives() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(anyInt(), anyCollection()))
                .thenReturn(3L);

        ConflictException erreur = assertThrows(ConflictException.class,
                () -> service().creer(demande(LIVRE_ID, ADHERENT_ID)));

        assertTrue(erreur.getMessage().startsWith("RG-03"),
                "le message doit nommer la règle enfreinte, or : " + erreur.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RG-03 : la troisième réservation active passe encore")
    void rg03_accepte_la_troisieme_reservation_active() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(anyInt(), anyCollection()))
                .thenReturn(2L);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO creee = service().creer(demande(LIVRE_ID, ADHERENT_ID));

        assertEquals(StatutReservation.EN_ATTENTE, creee.getStatut());
        verify(reservationRepository).save(any(Reservation.class));
    }

    // =========================================================================
    // RG-01, RG-02, RG-04
    // =========================================================================

    @Test
    @DisplayName("RG-01 : réserver un livre disponible est refusé en 409")
    void rg01_refuse_un_livre_disponible() {
        when(booksRepository.findById(LIVRE_ID)).thenReturn(Optional.of(livre(4)));
        when(usersRepository.findById(ADHERENT_ID)).thenReturn(Optional.of(adherent()));

        ConflictException erreur = assertThrows(ConflictException.class,
                () -> service().creer(demande(LIVRE_ID, ADHERENT_ID)));

        assertTrue(erreur.getMessage().startsWith("RG-01"), erreur.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RG-02 : deux réservations actives sur le même livre sont refusées en 409")
    void rg02_refuse_un_doublon_actif() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(true);

        ConflictException erreur = assertThrows(ConflictException.class,
                () -> service().creer(demande(LIVRE_ID, ADHERENT_ID)));

        assertTrue(erreur.getMessage().startsWith("RG-02"), erreur.getMessage());
    }

    @Test
    @DisplayName("RG-04 : l'expiration est fixée à sept jours après la réservation")
    void rg04_calcule_l_expiration_a_sept_jours() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(anyInt(), anyCollection()))
                .thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO creee = service().creer(demande(LIVRE_ID, ADHERENT_ID));

        assertEquals(creee.getDateReservation().plusDays(7), creee.getDateExpiration());
    }

    // =========================================================================
    // RG-05 et RG-06 — annulation
    // =========================================================================

    @Test
    @DisplayName("RG-05 : une réservation EN_ATTENTE peut être annulée")
    void rg05_annule_une_reservation_active() {
        Reservation reservation = reservation(StatutReservation.EN_ATTENTE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO annulee = service().annuler(1);

        assertEquals(StatutReservation.ANNULEE, annulee.getStatut());
    }

    @Test
    @DisplayName("RG-06 : une réservation déjà HONOREE ne change plus d'état")
    void rg06_refuse_de_toucher_a_un_statut_definitif() {
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(StatutReservation.HONOREE)));

        ConflictException erreur = assertThrows(ConflictException.class, () -> service().annuler(1));

        assertTrue(erreur.getMessage().contains("RG-06"), erreur.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    // =========================================================================
    // Validation et ressources inconnues
    // =========================================================================

    @Test
    @DisplayName("400 : le champ manquant est nommé dans le message")
    void validation_nomme_le_champ_manquant() {
        BadRequestException sansLivre = assertThrows(BadRequestException.class,
                () -> service().creer(demande(null, ADHERENT_ID)));
        assertTrue(sansLivre.getMessage().contains("livreId"), sansLivre.getMessage());

        BadRequestException sansAdherent = assertThrows(BadRequestException.class,
                () -> service().creer(demande(LIVRE_ID, null)));
        assertTrue(sansAdherent.getMessage().contains("adherentId"), sansAdherent.getMessage());

        assertThrows(BadRequestException.class, () -> service().creer(null));
    }

    @Test
    @DisplayName("404 : un livre inconnu est signalé avant toute règle de gestion")
    void livre_inconnu_donne_un_404() {
        when(booksRepository.findById(LIVRE_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().creer(demande(LIVRE_ID, ADHERENT_ID)));
    }

    @Test
    @DisplayName("404 : une réservation inconnue est signalée à la consultation")
    void reservation_inconnue_donne_un_404() {
        when(reservationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().consulter(99));
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private void simulerLivreIndisponibleEtAdherentConnu() {
        when(booksRepository.findById(LIVRE_ID)).thenReturn(Optional.of(livre(0)));
        when(usersRepository.findById(ADHERENT_ID)).thenReturn(Optional.of(adherent()));
    }

    private ReservationRequestDTO demande(Integer livreId, Integer adherentId) {
        ReservationRequestDTO demande = new ReservationRequestDTO();
        demande.setLivreId(livreId);
        demande.setAdherentId(adherentId);
        return demande;
    }

    private Books livre(int exemplaires) {
        Books livre = new Books();
        livre.setBookId(LIVRE_ID);
        livre.setBookName("Ville cruelle");
        livre.setNoOfCopies(exemplaires);
        return livre;
    }

    private Users adherent() {
        Users adherent = new Users();
        adherent.setUserId(ADHERENT_ID);
        adherent.setName("Marie Dupont");
        return adherent;
    }

    private Reservation reservation(StatutReservation statut) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(1);
        reservation.setLivre(livre(0));
        reservation.setAdherent(adherent());
        reservation.setDateReservation(LocalDateTime.now().minusDays(1));
        reservation.setDateExpiration(LocalDateTime.now().plusDays(6));
        reservation.setStatut(statut);
        return reservation;
    }
}
