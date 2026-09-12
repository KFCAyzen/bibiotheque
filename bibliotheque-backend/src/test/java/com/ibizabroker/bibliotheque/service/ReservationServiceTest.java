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
import com.ibizabroker.bibliotheque.exceptions.ForbiddenException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import com.ibizabroker.bibliotheque.security.RoleMetier;
import com.ibizabroker.bibliotheque.security.UtilisateurAuthentifie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles de gestion (RG) et règles de sécurité (RS) du module Réservation.
 *
 * Tests unitaires au sens strict : aucun contexte Spring, aucune base. Les
 * trois dépôts sont simulés, ce qui laisse le service seul face à ses règles
 * et rend l'exécution instantanée.
 *
 * Le demandeur est un UtilisateurAuthentifie construit à la main, exactement
 * comme JwtService le ferait depuis un token : ADHERENT (userId 2), AUTRE
 * ADHERENT (userId 3) ou BIBLIOTHECAIRE (userId 1).
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Integer LIVRE_ID = 7;
    private static final Integer ADHERENT_ID = 2;
    private static final Integer AUTRE_ADHERENT_ID = 3;
    private static final Integer BIBLIOTHECAIRE_ID = 1;

    private static final UtilisateurAuthentifie ADHERENT =
            principal(ADHERENT_ID, "a1", RoleMetier.ADHERENT);
    private static final UtilisateurAuthentifie AUTRE_ADHERENT =
            principal(AUTRE_ADHERENT_ID, "a2", RoleMetier.ADHERENT);
    private static final UtilisateurAuthentifie BIBLIOTHECAIRE =
            principal(BIBLIOTHECAIRE_ID, "admin", RoleMetier.BIBLIOTHECAIRE);

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
    @DisplayName("RG-03 : un adhérent ayant 3 réservations actives est refusé en 409")
    void rg03_refuse_la_quatrieme_reservation_active() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(eq(ADHERENT_ID), anyCollection()))
                .thenReturn(3L);

        ConflictException erreur = assertThrows(ConflictException.class,
                () -> service().creer(demande(LIVRE_ID, ADHERENT_ID), ADHERENT));

        assertTrue(erreur.getMessage().startsWith("RG-03"),
                "le message doit nommer la règle enfreinte, or : " + erreur.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RG-03 : un adhérent ayant 2 réservations actives peut en créer une troisième")
    void rg03_accepte_la_troisieme_reservation_active() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(eq(ADHERENT_ID), anyCollection()))
                .thenReturn(2L);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO creee = service().creer(demande(LIVRE_ID, ADHERENT_ID), ADHERENT);

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
                () -> service().creer(demande(LIVRE_ID, ADHERENT_ID), ADHERENT));

        assertTrue(erreur.getMessage().startsWith("RG-01"), erreur.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RG-01 : un livre sans exemplaire en rayon est réservable")
    void rg01_accepte_un_livre_indisponible() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(anyInt(), anyCollection()))
                .thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO creee = service().creer(demande(LIVRE_ID, ADHERENT_ID), ADHERENT);

        assertEquals(LIVRE_ID, creee.getLivreId());
        assertEquals(StatutReservation.EN_ATTENTE, creee.getStatut());
    }

    @Test
    @DisplayName("RG-02 : deux réservations actives sur le même livre sont refusées en 409")
    void rg02_refuse_un_doublon_actif() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(true);

        ConflictException erreur = assertThrows(ConflictException.class,
                () -> service().creer(demande(LIVRE_ID, ADHERENT_ID), ADHERENT));

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

        ReservationResponseDTO creee = service().creer(demande(LIVRE_ID, ADHERENT_ID), ADHERENT);

        assertEquals(creee.getDateReservation().plusDays(7), creee.getDateExpiration());
    }

    // =========================================================================
    // RG-05 et RG-06 — annulation
    // =========================================================================

    @Test
    @DisplayName("RG-05 : une réservation EN_ATTENTE peut être annulée par son propriétaire")
    void rg05_annule_une_reservation_active() {
        Reservation reservation = reservation(StatutReservation.EN_ATTENTE);
        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO annulee = service().annuler(1, ADHERENT);

        assertEquals(StatutReservation.ANNULEE, annulee.getStatut());
    }

    @Test
    @DisplayName("RG-06 : une réservation déjà HONOREE ne change plus d'état")
    void rg06_refuse_de_toucher_a_un_statut_definitif() {
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(StatutReservation.HONOREE)));

        ConflictException erreur = assertThrows(ConflictException.class,
                () -> service().annuler(1, ADHERENT));

        assertTrue(erreur.getMessage().contains("RG-06"), erreur.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    // =========================================================================
    // RS-03 — un adhérent ne touche qu'à ses réservations
    // =========================================================================

    @Test
    @DisplayName("RS-03 : consulter la réservation d'un autre adhérent est refusé en 403")
    void rs03_refuse_la_consultation_de_la_reservation_d_un_autre() {
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(StatutReservation.EN_ATTENTE)));

        ForbiddenException erreur = assertThrows(ForbiddenException.class,
                () -> service().consulter(1, AUTRE_ADHERENT));

        assertTrue(erreur.getMessage().startsWith("RS-03"), erreur.getMessage());
    }

    @Test
    @DisplayName("RS-03 : annuler la réservation d'un autre adhérent est refusé en 403, sans rien modifier")
    void rs03_refuse_l_annulation_de_la_reservation_d_un_autre() {
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(StatutReservation.EN_ATTENTE)));

        assertThrows(ForbiddenException.class, () -> service().annuler(1, AUTRE_ADHERENT));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RS-03 : le bibliothécaire consulte et annule les réservations de n'importe qui")
    void rs03_laisse_le_bibliothecaire_agir_sur_toutes_les_reservations() {
        when(reservationRepository.findById(1))
                .thenReturn(Optional.of(reservation(StatutReservation.EN_ATTENTE)));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(ADHERENT_ID, service().consulter(1, BIBLIOTHECAIRE).getAdherentId());
        assertEquals(StatutReservation.ANNULEE, service().annuler(1, BIBLIOTHECAIRE).getStatut());
    }

    // =========================================================================
    // RS-04 — l'identité vient du token, pas du corps
    // =========================================================================

    @Test
    @DisplayName("RS-04 : un adhérent qui envoie l'adherentId d'un autre est refusé en 403")
    void rs04_refuse_une_reservation_au_nom_d_un_autre_adherent() {
        ForbiddenException erreur = assertThrows(ForbiddenException.class,
                () -> service().creer(demande(LIVRE_ID, AUTRE_ADHERENT_ID), ADHERENT));

        assertTrue(erreur.getMessage().startsWith("RS-04"), erreur.getMessage());
        verify(reservationRepository, never()).save(any());
        // Le refus tombe avant tout accès aux données : l'usurpateur n'apprend
        // même pas si le livre ou l'autre adhérent existent.
        verify(booksRepository, never()).findById(any());
        verify(usersRepository, never()).findById(any());
    }

    @Test
    @DisplayName("RS-04 : sans adherentId dans le corps, la réservation est au nom du porteur du token")
    void rs04_prend_l_identite_dans_le_token_quand_le_corps_ne_dit_rien() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(anyInt(), anyCollection()))
                .thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO creee = service().creer(demande(LIVRE_ID, null), ADHERENT);

        ArgumentCaptor<Reservation> sauvegardee = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(sauvegardee.capture());
        assertEquals(ADHERENT_ID, sauvegardee.getValue().getAdherent().getUserId());
        assertEquals(ADHERENT_ID, creee.getAdherentId());
    }

    @Test
    @DisplayName("RS-04 : le bibliothécaire réserve au nom de l'adhérent désigné dans le corps")
    void rs04_laisse_le_bibliothecaire_reserver_pour_un_adherent() {
        simulerLivreIndisponibleEtAdherentConnu();
        when(reservationRepository.existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                anyInt(), anyInt(), anyCollection())).thenReturn(false);
        when(reservationRepository.countByAdherent_UserIdAndStatutIn(anyInt(), anyCollection()))
                .thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO creee = service().creer(demande(LIVRE_ID, ADHERENT_ID), BIBLIOTHECAIRE);

        assertEquals(ADHERENT_ID, creee.getAdherentId());
    }

    // =========================================================================
    // RS-05 — la liste d'un adhérent ne contient que les siennes
    // =========================================================================

    @Test
    @DisplayName("RS-05 : la liste d'un adhérent est filtrée sur son identifiant, jamais findAll")
    void rs05_restreint_la_liste_aux_reservations_de_l_adherent() {
        when(reservationRepository.findByAdherent_UserId(ADHERENT_ID))
                .thenReturn(Collections.singletonList(reservation(StatutReservation.EN_ATTENTE)));

        assertEquals(1, service().lister(null, null, ADHERENT).size());

        verify(reservationRepository).findByAdherent_UserId(ADHERENT_ID);
        verify(reservationRepository, never()).findAll();
    }

    @Test
    @DisplayName("RS-05 : un adhérent qui demande la liste d'un autre est refusé en 403")
    void rs05_refuse_le_filtre_sur_un_autre_adherent() {
        ForbiddenException erreur = assertThrows(ForbiddenException.class,
                () -> service().lister(null, AUTRE_ADHERENT_ID, ADHERENT));

        assertTrue(erreur.getMessage().startsWith("RS-05"), erreur.getMessage());
    }

    @Test
    @DisplayName("RS-05 : le bibliothécaire obtient toutes les réservations")
    void rs05_laisse_le_bibliothecaire_tout_lister() {
        when(reservationRepository.findAll()).thenReturn(Collections.emptyList());

        service().lister(null, null, BIBLIOTHECAIRE);

        verify(reservationRepository).findAll();
    }

    // =========================================================================
    // Validation et ressources inconnues
    // =========================================================================

    @Test
    @DisplayName("400 : le champ manquant est nommé dans le message")
    void validation_nomme_le_champ_manquant() {
        BadRequestException sansLivre = assertThrows(BadRequestException.class,
                () -> service().creer(demande(null, ADHERENT_ID), ADHERENT));
        assertTrue(sansLivre.getMessage().contains("livreId"), sansLivre.getMessage());

        // Seul le bibliothécaire doit dire pour qui il réserve.
        BadRequestException sansAdherent = assertThrows(BadRequestException.class,
                () -> service().creer(demande(LIVRE_ID, null), BIBLIOTHECAIRE));
        assertTrue(sansAdherent.getMessage().contains("adherentId"), sansAdherent.getMessage());

        assertThrows(BadRequestException.class, () -> service().creer(null, ADHERENT));
        assertThrows(BadRequestException.class, () -> service().creer(null, BIBLIOTHECAIRE));
    }

    @Test
    @DisplayName("404 : un livre inconnu est signalé avant toute règle de gestion")
    void livre_inconnu_donne_un_404() {
        when(booksRepository.findById(LIVRE_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service().creer(demande(LIVRE_ID, ADHERENT_ID), ADHERENT));
    }

    @Test
    @DisplayName("404 : une réservation inconnue est signalée à la consultation")
    void reservation_inconnue_donne_un_404() {
        when(reservationRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().consulter(99, ADHERENT));
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private static UtilisateurAuthentifie principal(Integer userId, String username, RoleMetier role) {
        Set<GrantedAuthority> autorites = Collections.singleton(new SimpleGrantedAuthority(role.autorite()));
        return new UtilisateurAuthentifie(userId, username, "secret", username, EnumSet.of(role), autorites);
    }

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
