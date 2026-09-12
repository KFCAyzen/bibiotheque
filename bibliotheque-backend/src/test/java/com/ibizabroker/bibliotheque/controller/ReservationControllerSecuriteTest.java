package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.configuration.AccesInterditHandler;
import com.ibizabroker.bibliotheque.configuration.JwtAuthenticationEntryPoint;
import com.ibizabroker.bibliotheque.configuration.JwtRequestFilter;
import com.ibizabroker.bibliotheque.configuration.WebSecurityConfiguration;
import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.Role;
import com.ibizabroker.bibliotheque.entity.StatutReservation;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.security.UtilisateurAuthentifie;
import com.ibizabroker.bibliotheque.service.JwtService;
import com.ibizabroker.bibliotheque.service.ReservationService;
import com.ibizabroker.bibliotheque.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration des règles de sécurité sur les routes /api/reservations.
 *
 * CE QUI EST RÉEL
 * La chaîne de filtres Spring Security telle que WebSecurityConfiguration la
 * déclare, JwtRequestFilter et JwtUtil (les tokens sont de vrais JWT signés),
 * JwtService, le contrôleur, ReservationService avec ses règles, et
 * ReservationExceptionHandler. Une requête traverse exactement le chemin
 * qu'elle suivrait en production, de l'en-tête Authorization jusqu'au corps
 * JSON de la réponse.
 *
 * CE QUI EST SIMULÉ
 * Les trois dépôts JPA, donc la base. Le projet n'embarque aucune base en
 * mémoire et pom.xml est intouchable ; simuler la persistance est ce qui
 * permet à ce test de tourner par « mvn test », sans PostgreSQL ni Docker.
 *
 * LES TROIS COMPTES
 * a1 (userId 2) et a2 (userId 3), ADHERENT ; admin (userId 1),
 * BIBLIOTHECAIRE. La réservation 100 appartient à a1, la 101 à a2 — ce sont
 * les identifiants du jeu de données docker/fixture-reservation.sql.
 */
@WebMvcTest(ReservationController.class)
@Import({
        WebSecurityConfiguration.class,
        JwtRequestFilter.class,
        JwtAuthenticationEntryPoint.class,
        AccesInterditHandler.class,
        JwtUtil.class,
        JwtService.class,
        ReservationService.class
})
class ReservationControllerSecuriteTest {

    private static final String RESERVATIONS = "/api/reservations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private ReservationRepository reservationRepository;

    @MockBean
    private BooksRepository booksRepository;

    @MockBean
    private UsersRepository usersRepository;

    private Users a1;
    private Users a2;
    private Users admin;

    @BeforeEach
    void troisComptesEnBase() {
        a1 = utilisateur(2, "a1", "User");
        a2 = utilisateur(3, "a2", "User");
        admin = utilisateur(1, "admin", "Admin");

        when(usersRepository.findByUsername("a1")).thenReturn(Optional.of(a1));
        when(usersRepository.findByUsername("a2")).thenReturn(Optional.of(a2));
        when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        when(reservationRepository.findById(100)).thenReturn(Optional.of(reservation(100, a1)));
        when(reservationRepository.findById(101)).thenReturn(Optional.of(reservation(101, a2)));
    }

    // =========================================================================
    // RS-01 — sans token : 401
    // =========================================================================

    @Test
    @DisplayName("RS-01 : GET /api/reservations sans token répond 401")
    void lister_sans_token_repond_401() throws Exception {
        mockMvc.perform(get(RESERVATIONS))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.statut").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Authentification requise")));
    }

    @Test
    @DisplayName("RS-01 : chaque route de réservation répond 401 à l'anonyme, jamais 403")
    void toutes_les_routes_repondent_401_sans_token() throws Exception {
        mockMvc.perform(post(RESERVATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\": 2}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RESERVATIONS + "/100"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(RESERVATIONS + "/100/annuler"))
                .andExpect(status().isUnauthorized());
        // DELETE est soumis à une règle de rôle : sans identité, c'est quand
        // même 401 — on ne peut pas manquer d'un rôle sans être quelqu'un.
        mockMvc.perform(delete(RESERVATIONS + "/100"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RS-01 : un token expiré répond 401 en le disant")
    void token_expire_repond_401_avec_le_message() throws Exception {
        String tokenExpire = jwtUtil.generateToken(UtilisateurAuthentifie.depuis(a1), -60);

        mockMvc.perform(get(RESERVATIONS).header("Authorization", "Bearer " + tokenExpire))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("expiré")));
    }

    @Test
    @DisplayName("RS-01 : un token illisible répond 401, pas 500")
    void token_illisible_repond_401() throws Exception {
        mockMvc.perform(get(RESERVATIONS).header("Authorization", "Bearer pas.un.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("invalide")));
    }

    // =========================================================================
    // RS-05 — avec un token ADHERENT : 200, et seulement les siennes
    // =========================================================================

    @Test
    @DisplayName("RS-05 : GET /api/reservations avec un token ADHERENT répond 200 avec ses seules réservations")
    void lister_avec_token_adherent_repond_200_avec_ses_reservations() throws Exception {
        when(reservationRepository.findByAdherent_UserId(2))
                .thenReturn(Collections.singletonList(reservation(100, a1)));

        mockMvc.perform(get(RESERVATIONS).header("Authorization", bearer(a1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].adherentId").value(2));

        verify(reservationRepository, never()).findAll();
    }

    @Test
    @DisplayName("RS-05 : un ADHERENT qui filtre sur l'adherentId d'un autre reçoit 403")
    void lister_avec_le_filtre_d_un_autre_adherent_repond_403() throws Exception {
        mockMvc.perform(get(RESERVATIONS).param("adherentId", "3").header("Authorization", bearer(a1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("RS-05")));
    }

    @Test
    @DisplayName("GET /api/reservations avec un token BIBLIOTHECAIRE renvoie toutes les réservations")
    void lister_avec_token_bibliothecaire_renvoie_tout() throws Exception {
        when(reservationRepository.findAll())
                .thenReturn(java.util.Arrays.asList(reservation(100, a1), reservation(101, a2)));

        mockMvc.perform(get(RESERVATIONS).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // =========================================================================
    // RS-03 — la réservation d'un autre : 403
    // =========================================================================

    @Test
    @DisplayName("RS-03 : un ADHERENT qui consulte la réservation d'un autre reçoit 403")
    void consulter_la_reservation_d_un_autre_repond_403() throws Exception {
        mockMvc.perform(get(RESERVATIONS + "/101").header("Authorization", bearer(a1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statut").value(403))
                .andExpect(jsonPath("$.message").value(containsString("RS-03")));
    }

    @Test
    @DisplayName("RS-03 : un ADHERENT consulte sa propre réservation en 200")
    void consulter_sa_propre_reservation_repond_200() throws Exception {
        mockMvc.perform(get(RESERVATIONS + "/100").header("Authorization", bearer(a1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @DisplayName("RS-03 : un ADHERENT qui annule la réservation d'un autre reçoit 403, rien n'est modifié")
    void annuler_la_reservation_d_un_autre_repond_403() throws Exception {
        mockMvc.perform(patch(RESERVATIONS + "/101/annuler").header("Authorization", bearer(a1)))
                .andExpect(status().isForbidden());

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Le BIBLIOTHECAIRE consulte la réservation de n'importe qui")
    void bibliothecaire_consulte_toute_reservation() throws Exception {
        mockMvc.perform(get(RESERVATIONS + "/101").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adherentId").value(3));
    }

    // =========================================================================
    // RS-02 — action réservée au bibliothécaire : 403 pour l'adhérent
    // =========================================================================

    @Test
    @DisplayName("RS-02 : DELETE par un ADHERENT répond 403, même sur sa propre réservation")
    void supprimer_par_un_adherent_repond_403() throws Exception {
        mockMvc.perform(delete(RESERVATIONS + "/100").header("Authorization", bearer(a1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statut").value(403));

        verify(reservationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("RS-02 : DELETE par le BIBLIOTHECAIRE répond 204")
    void supprimer_par_le_bibliothecaire_repond_204() throws Exception {
        mockMvc.perform(delete(RESERVATIONS + "/100").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        verify(reservationRepository).delete(any(Reservation.class));
    }

    // =========================================================================
    // RS-04 — l'identité vient du token
    // =========================================================================

    @Test
    @DisplayName("RS-04 : un ADHERENT qui envoie l'adherentId d'un autre reçoit 403")
    void creer_au_nom_d_un_autre_repond_403() throws Exception {
        mockMvc.perform(post(RESERVATIONS).header("Authorization", bearer(a1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\": 2, \"adherentId\": 3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("RS-04")));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RS-04 : sans adherentId, la réservation est créée au nom du porteur du token")
    void creer_sans_adherent_id_reserve_pour_le_porteur_du_token() throws Exception {
        Books livre = new Books();
        livre.setBookId(2);
        livre.setBookName("L2 — L'Étranger");
        livre.setNoOfCopies(0);
        when(booksRepository.findById(2)).thenReturn(Optional.of(livre));
        when(usersRepository.findById(2)).thenReturn(Optional.of(a1));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setReservationId(102);
            return r;
        });

        mockMvc.perform(post(RESERVATIONS).header("Authorization", bearer(a1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adherentId").value(2))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private String bearer(Users user) {
        return "Bearer " + jwtUtil.generateToken(UtilisateurAuthentifie.depuis(user));
    }

    private static Users utilisateur(int userId, String username, String roleName) {
        Role role = new Role();
        role.setRoleId("Admin".equals(roleName) ? 1 : 2);
        role.setRoleName(roleName);

        Users user = new Users();
        user.setUserId(userId);
        user.setUsername(username);
        user.setName(username.toUpperCase());
        user.setPassword("$2a$10$hache.sans.importance.ici.le.token.suffit");
        user.setRole(Collections.singleton(role));
        return user;
    }

    private static Reservation reservation(int id, Users adherent) {
        Books livre = new Books();
        livre.setBookId(2);
        livre.setBookName("L2 — L'Étranger");
        livre.setNoOfCopies(0);

        Reservation reservation = new Reservation();
        reservation.setReservationId(id);
        reservation.setLivre(livre);
        reservation.setAdherent(adherent);
        reservation.setDateReservation(LocalDateTime.now().minusDays(1));
        reservation.setDateExpiration(LocalDateTime.now().plusDays(6));
        reservation.setStatut(StatutReservation.EN_ATTENTE);
        return reservation;
    }
}
