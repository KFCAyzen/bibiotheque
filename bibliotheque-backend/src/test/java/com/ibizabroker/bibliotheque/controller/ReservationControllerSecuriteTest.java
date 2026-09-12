package com.ibizabroker.bibliotheque.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.Role;
import com.ibizabroker.bibliotheque.entity.StatutReservation;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.security.UtilisateurAuthentifie;
import com.ibizabroker.bibliotheque.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * TOUT EST RÉEL
 * Le contexte Spring complet, la chaîne de filtres Spring Security, les
 * jetons obtenus par un vrai POST /authenticate (mot de passe haché en
 * BCrypt), le contrôleur, ReservationService, ReservationExceptionHandler et
 * la base PostgreSQL du projet, via le profil « test ». Une requête suit
 * exactement le chemin qu'elle suivrait en production.
 *
 * Prérequis : docker compose up -d db.
 *
 * ISOLATION
 * @Transactional sur la classe : chaque méthode s'exécute dans une
 * transaction annulée à la fin, jeu de données compris. La base retrouve son
 * état initial, quel que soit le résultat du test. Les noms de compte portent
 * un préfixe « it- » pour ne jamais entrer en collision avec le jeu de
 * démonstration (admin, a1, a2, a3).
 *
 * LES TROIS COMPTES
 * it-a1 et it-a2, ADHERENT (rôle User) ; it-admin, BIBLIOTHECAIRE (rôle
 * Admin). Une réservation pour it-a1, une pour it-a2 — la configuration que
 * l'énoncé demande pour la démonstration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationControllerSecuriteTest {

    private static final String RESERVATIONS = "/api/reservations";
    private static final String MOT_DE_PASSE = "admin123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Users a1;
    private Users a2;
    private Users admin;
    private Books livreEmprunte;
    private Reservation reservationDeA1;
    private Reservation reservationDeA2;

    @BeforeEach
    void jeuDeDonnees() {
        a1 = usersRepository.save(utilisateur("it-a1", "User"));
        a2 = usersRepository.save(utilisateur("it-a2", "User"));
        admin = usersRepository.save(utilisateur("it-admin", "Admin"));

        livreEmprunte = new Books();
        livreEmprunte.setBookName("IT — livre emprunté");
        livreEmprunte.setBookAuthor("Auteur");
        livreEmprunte.setBookGenre("Test");
        livreEmprunte.setNoOfCopies(0);
        livreEmprunte = booksRepository.save(livreEmprunte);

        Books autreLivreEmprunte = new Books();
        autreLivreEmprunte.setBookName("IT — autre livre emprunté");
        autreLivreEmprunte.setBookAuthor("Auteur");
        autreLivreEmprunte.setBookGenre("Test");
        autreLivreEmprunte.setNoOfCopies(0);
        autreLivreEmprunte = booksRepository.save(autreLivreEmprunte);

        reservationDeA1 = reservationRepository.save(reservation(livreEmprunte, a1));
        reservationDeA2 = reservationRepository.save(reservation(autreLivreEmprunte, a2));
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
                        .content("{\"livreId\": " + livreEmprunte.getBookId() + "}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RESERVATIONS + "/" + reservationDeA1.getReservationId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(RESERVATIONS + "/" + reservationDeA1.getReservationId() + "/annuler"))
                .andExpect(status().isUnauthorized());
        // DELETE est soumis à une règle de rôle : sans identité, c'est quand
        // même 401 — on ne peut pas manquer d'un rôle sans être quelqu'un.
        mockMvc.perform(delete(RESERVATIONS + "/" + reservationDeA1.getReservationId()))
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
        mockMvc.perform(get(RESERVATIONS).header("Authorization", bearer("it-a1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(reservationDeA1.getReservationId()))
                .andExpect(jsonPath("$[0].adherentId").value(a1.getUserId()));
    }

    @Test
    @DisplayName("RS-05 : un ADHERENT qui filtre sur l'adherentId d'un autre reçoit 403")
    void lister_avec_le_filtre_d_un_autre_adherent_repond_403() throws Exception {
        mockMvc.perform(get(RESERVATIONS).param("adherentId", String.valueOf(a2.getUserId()))
                        .header("Authorization", bearer("it-a1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("RS-05")));
    }

    @Test
    @DisplayName("GET /api/reservations avec un token BIBLIOTHECAIRE renvoie les réservations de tous")
    void lister_avec_token_bibliothecaire_renvoie_tout() throws Exception {
        MvcResult resultat = mockMvc.perform(get(RESERVATIONS).header("Authorization", bearer("it-admin")))
                .andExpect(status().isOk())
                .andReturn();

        // La base peut contenir d'autres réservations (jeu de démonstration) :
        // on vérifie la présence des deux nôtres, pas le total.
        JsonNode liste = objectMapper.readTree(resultat.getResponse().getContentAsString());
        assertTrue(contientReservation(liste, reservationDeA1.getReservationId()));
        assertTrue(contientReservation(liste, reservationDeA2.getReservationId()));
    }

    // =========================================================================
    // RS-03 — la réservation d'un autre : 403
    // =========================================================================

    @Test
    @DisplayName("RS-03 : un ADHERENT qui consulte la réservation d'un autre reçoit 403")
    void consulter_la_reservation_d_un_autre_repond_403() throws Exception {
        mockMvc.perform(get(RESERVATIONS + "/" + reservationDeA2.getReservationId())
                        .header("Authorization", bearer("it-a1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statut").value(403))
                .andExpect(jsonPath("$.message").value(containsString("RS-03")));
    }

    @Test
    @DisplayName("RS-03 : un ADHERENT consulte sa propre réservation en 200")
    void consulter_sa_propre_reservation_repond_200() throws Exception {
        mockMvc.perform(get(RESERVATIONS + "/" + reservationDeA1.getReservationId())
                        .header("Authorization", bearer("it-a1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationDeA1.getReservationId()));
    }

    @Test
    @DisplayName("RS-03 : un ADHERENT qui annule la réservation d'un autre reçoit 403, rien n'est modifié")
    void annuler_la_reservation_d_un_autre_repond_403() throws Exception {
        mockMvc.perform(patch(RESERVATIONS + "/" + reservationDeA2.getReservationId() + "/annuler")
                        .header("Authorization", bearer("it-a1")))
                .andExpect(status().isForbidden());

        Reservation enBase = reservationRepository.findById(reservationDeA2.getReservationId()).get();
        assertEquals(StatutReservation.EN_ATTENTE, enBase.getStatut());
    }

    @Test
    @DisplayName("Le BIBLIOTHECAIRE consulte et annule la réservation de n'importe qui")
    void bibliothecaire_agit_sur_toute_reservation() throws Exception {
        mockMvc.perform(get(RESERVATIONS + "/" + reservationDeA2.getReservationId())
                        .header("Authorization", bearer("it-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adherentId").value(a2.getUserId()));

        mockMvc.perform(patch(RESERVATIONS + "/" + reservationDeA2.getReservationId() + "/annuler")
                        .header("Authorization", bearer("it-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"));
    }

    // =========================================================================
    // RS-02 — action réservée au bibliothécaire : 403 pour l'adhérent
    // =========================================================================

    @Test
    @DisplayName("RS-02 : DELETE par un ADHERENT répond 403, même sur sa propre réservation")
    void supprimer_par_un_adherent_repond_403() throws Exception {
        mockMvc.perform(delete(RESERVATIONS + "/" + reservationDeA1.getReservationId())
                        .header("Authorization", bearer("it-a1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statut").value(403))
                .andExpect(jsonPath("$.message").value(containsString("RS-02")));

        assertTrue(reservationRepository.findById(reservationDeA1.getReservationId()).isPresent());
    }

    @Test
    @DisplayName("RS-02 : DELETE par le BIBLIOTHECAIRE répond 204 et la ligne disparaît")
    void supprimer_par_le_bibliothecaire_repond_204() throws Exception {
        mockMvc.perform(delete(RESERVATIONS + "/" + reservationDeA1.getReservationId())
                        .header("Authorization", bearer("it-admin")))
                .andExpect(status().isNoContent());

        assertFalse(reservationRepository.findById(reservationDeA1.getReservationId()).isPresent());
    }

    // =========================================================================
    // RS-04 — l'identité vient du token
    // =========================================================================

    @Test
    @DisplayName("RS-04 : un ADHERENT qui envoie l'adherentId d'un autre reçoit 403")
    void creer_au_nom_d_un_autre_repond_403() throws Exception {
        mockMvc.perform(post(RESERVATIONS).header("Authorization", bearer("it-a1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\": " + livreEmprunte.getBookId()
                                + ", \"adherentId\": " + a2.getUserId() + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("RS-04")));

        List<Reservation> deA2 = reservationRepository.findByAdherent_UserId(a2.getUserId());
        assertEquals(1, deA2.size(), "aucune réservation ne doit avoir été créée au nom de it-a2");
    }

    @Test
    @DisplayName("RS-04 : sans adherentId, la réservation est créée au nom du porteur du token")
    void creer_sans_adherent_id_reserve_pour_le_porteur_du_token() throws Exception {
        // it-a2 n'a pas encore réservé livreEmprunte : RG-02 ne s'oppose pas.
        mockMvc.perform(post(RESERVATIONS).header("Authorization", bearer("it-a2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livreId\": " + livreEmprunte.getBookId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adherentId").value(a2.getUserId()))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));

        assertEquals(2, reservationRepository.findByAdherent_UserId(a2.getUserId()).size());
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /** Un vrai jeton, obtenu comme le front l'obtient : POST /authenticate. */
    private String bearer(String username) throws Exception {
        MvcResult resultat = mockMvc.perform(post("/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + username + "\", \"password\": \"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reponse = objectMapper.readTree(resultat.getResponse().getContentAsString());
        return "Bearer " + reponse.get("jwtToken").asText();
    }

    private Users utilisateur(String username, String roleName) {
        Users user = new Users();
        user.setUsername(username);
        user.setName(username.toUpperCase());
        user.setPassword(passwordEncoder.encode(MOT_DE_PASSE));
        user.setRole(Collections.singleton(role(roleName)));
        return user;
    }

    /**
     * Le rôle « User » ou « Admin » tel qu'il existe en base — c'est sur ces
     * deux lignes de la séance 1 que RoleMetier s'appuie. Il n'est créé que si
     * la base est vierge, jamais en doublon.
     */
    private Role role(String roleName) {
        List<Role> existants = entityManager
                .createQuery("select r from Role r where r.roleName = :nom", Role.class)
                .setParameter("nom", roleName)
                .getResultList();
        if (!existants.isEmpty()) {
            return existants.get(0);
        }
        Role role = new Role();
        role.setRoleName(roleName);
        entityManager.persist(role);
        return role;
    }

    private static Reservation reservation(Books livre, Users adherent) {
        Reservation reservation = new Reservation();
        reservation.setLivre(livre);
        reservation.setAdherent(adherent);
        reservation.setDateReservation(LocalDateTime.now().minusDays(1));
        reservation.setDateExpiration(LocalDateTime.now().plusDays(6));
        reservation.setStatut(StatutReservation.EN_ATTENTE);
        return reservation;
    }

    private static boolean contientReservation(JsonNode liste, Integer id) {
        for (JsonNode element : liste) {
            if (element.get("id").asInt() == id) {
                return true;
            }
        }
        return false;
    }
}
