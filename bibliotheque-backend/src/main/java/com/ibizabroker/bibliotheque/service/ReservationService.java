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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Toute la logique du module Réservation.
 *
 * Le contrôleur ne fait que router : validation, chargement des ressources,
 * application des six règles de gestion et conversion en DTO se passent ici.
 * L'entité Reservation n'en sort jamais.
 *
 * Injection par constructeur plutôt que par @Autowired sur les champs : les
 * dépendances deviennent explicites, et le service est instanciable tel quel
 * dans ReservationServiceTest, sans contexte Spring.
 */
@Service
public class ReservationService {

    /** RG-03 : plafond de réservations actives simultanées pour un adhérent. */
    private static final long NOMBRE_MAX_RESERVATIONS_ACTIVES = 3;

    /** RG-04 : durée de validité d'une réservation. */
    private static final int DUREE_VALIDITE_EN_JOURS = 7;

    private final ReservationRepository reservationRepository;
    private final BooksRepository booksRepository;
    private final UsersRepository usersRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              BooksRepository booksRepository,
                              UsersRepository usersRepository) {
        this.reservationRepository = reservationRepository;
        this.booksRepository = booksRepository;
        this.usersRepository = usersRepository;
    }

    // =========================================================================
    // Création
    // =========================================================================

    /**
     * Crée une réservation.
     *
     * Ordre des contrôles, du plus général au plus spécifique : forme de la
     * requête (400), existence des ressources (404), puis règles de gestion
     * (409). Un identifiant inconnu doit être signalé comme tel avant qu'on ne
     * se prononce sur la disponibilité du livre, sinon RG-01 masquerait le
     * vrai problème.
     */
    @Transactional
    public ReservationResponseDTO creer(ReservationRequestDTO demande) {
        valider(demande);

        Books livre = booksRepository.findById(demande.getLivreId())
                .orElseThrow(() -> new NotFoundException(
                        "Aucun livre avec l'identifiant " + demande.getLivreId() + "."));

        Users adherent = usersRepository.findById(demande.getAdherentId())
                .orElseThrow(() -> new NotFoundException(
                        "Aucun adhérent avec l'identifiant " + demande.getAdherentId() + "."));

        verifierLivreIndisponible(livre);
        verifierAbsenceDeDoublon(livre, adherent);
        verifierPlafondDeReservations(adherent);

        LocalDateTime maintenant = LocalDateTime.now();

        Reservation reservation = new Reservation();
        reservation.setLivre(livre);
        reservation.setAdherent(adherent);
        // RG-04 : les deux dates sont posées par le serveur, jamais reçues.
        reservation.setDateReservation(maintenant);
        reservation.setDateExpiration(maintenant.plusDays(DUREE_VALIDITE_EN_JOURS));
        reservation.setStatut(StatutReservation.EN_ATTENTE);

        return versDTO(reservationRepository.save(reservation));
    }

    // =========================================================================
    // Lecture
    // =========================================================================

    /**
     * Liste les réservations, éventuellement filtrées par statut et par adhérent.
     *
     * Les deux filtres sont indépendants et cumulables. Un adhérent inconnu ne
     * déclenche pas de 404 : un filtre sans résultat renvoie une liste vide,
     * ce qui est la réponse correcte à « montre-moi ses réservations ».
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> lister(String statutDemande, Integer adherentId) {
        StatutReservation statut = convertirStatut(statutDemande);

        List<Reservation> reservations;
        if (statut != null && adherentId != null) {
            reservations = reservationRepository.findByAdherent_UserIdAndStatut(adherentId, statut);
        } else if (statut != null) {
            reservations = reservationRepository.findByStatut(statut);
        } else if (adherentId != null) {
            reservations = reservationRepository.findByAdherent_UserId(adherentId);
        } else {
            reservations = reservationRepository.findAll();
        }

        return versDTO(reservations);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDTO consulter(Integer id) {
        return versDTO(chargerReservation(id));
    }

    /** Bonus : les réservations dont la validité est écoulée. */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> listerExpirees() {
        return versDTO(reservationRepository.findByStatut(StatutReservation.EXPIREE));
    }

    // =========================================================================
    // Transitions d'état
    // =========================================================================

    /**
     * Annule une réservation.
     *
     * RG-05 et RG-06 sont les deux faces d'un même contrôle : on n'annule que
     * depuis un statut actif, donc jamais depuis un statut définitif.
     */
    @Transactional
    public ReservationResponseDTO annuler(Integer id) {
        Reservation reservation = chargerReservation(id);

        if (reservation.getStatut().estDefinitif()) {
            throw new ConflictException(
                    "RG-05 : seule une réservation EN_ATTENTE ou DISPONIBLE peut être annulée ; "
                            + "celle-ci est " + reservation.getStatut() + ". "
                            + "RG-06 : un statut définitif ne peut plus changer.");
        }

        reservation.setStatut(StatutReservation.ANNULEE);
        return versDTO(reservationRepository.save(reservation));
    }

    @Transactional
    public void supprimer(Integer id) {
        reservationRepository.delete(chargerReservation(id));
    }

    /**
     * Bonus : bascule en EXPIREE toute réservation active dont la date
     * d'expiration est dépassée. Déclenché par ReservationExpirationScheduler.
     *
     * Ne touche que les statuts actifs : une réservation déjà ANNULEE ou
     * HONOREE reste dans son état définitif — RG-06 vaut aussi pour le serveur.
     *
     * @return le nombre de réservations basculées
     */
    @Transactional
    public int expirerLesReservationsEchues() {
        List<Reservation> echues = reservationRepository.findByStatutInAndDateExpirationBefore(
                StatutReservation.statutsActifs(), LocalDateTime.now());

        echues.forEach(reservation -> reservation.setStatut(StatutReservation.EXPIREE));
        reservationRepository.saveAll(echues);

        return echues.size();
    }

    // =========================================================================
    // Validation et règles de gestion
    // =========================================================================

    /**
     * Validation d'entrée, faite à la main.
     *
     * Le projet n'embarque pas hibernate-validator (pom.xml intouchable depuis
     * la séance 1) : @NotNull et @Valid seraient ignorés en silence, ce qui est
     * pire qu'une validation explicite. Le message nomme le champ manquant,
     * comme l'exige l'énoncé.
     */
    private void valider(ReservationRequestDTO demande) {
        if (demande == null) {
            throw new BadRequestException(
                    "Le corps de la requête est obligatoire : { livreId, adherentId }.");
        }
        if (demande.getLivreId() == null) {
            throw new BadRequestException("Le champ « livreId » est obligatoire.");
        }
        if (demande.getAdherentId() == null) {
            throw new BadRequestException("Le champ « adherentId » est obligatoire.");
        }
    }

    /**
     * RG-01 : on ne réserve que ce qu'on ne peut pas emprunter.
     *
     * La disponibilité se lit sur noOfCopies, le compteur que BorrowController
     * décrémente à l'emprunt et incrémente au retour.
     */
    private void verifierLivreIndisponible(Books livre) {
        boolean disponible = livre.getNoOfCopies() != null && livre.getNoOfCopies() > 0;
        if (disponible) {
            throw new ConflictException(
                    "RG-01 : le livre « " + livre.getBookName() + " » est disponible ("
                            + livre.getNoOfCopies() + " exemplaire(s) en rayon) ; "
                            + "il doit être emprunté, pas réservé.");
        }
    }

    /** RG-02 : une seule réservation active par couple (adhérent, livre). */
    private void verifierAbsenceDeDoublon(Books livre, Users adherent) {
        boolean dejaReserve = reservationRepository
                .existsByLivre_BookIdAndAdherent_UserIdAndStatutIn(
                        livre.getBookId(), adherent.getUserId(), StatutReservation.statutsActifs());

        if (dejaReserve) {
            throw new ConflictException(
                    "RG-02 : " + adherent.getName() + " a déjà une réservation active sur « "
                            + livre.getBookName() + " ».");
        }
    }

    /** RG-03 : trois réservations actives au maximum, simultanément. */
    private void verifierPlafondDeReservations(Users adherent) {
        long actives = reservationRepository.countByAdherent_UserIdAndStatutIn(
                adherent.getUserId(), StatutReservation.statutsActifs());

        if (actives >= NOMBRE_MAX_RESERVATIONS_ACTIVES) {
            throw new ConflictException(
                    "RG-03 : " + adherent.getName() + " a déjà " + actives
                            + " réservations actives ; le maximum est de "
                            + NOMBRE_MAX_RESERVATIONS_ACTIVES + ".");
        }
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    private Reservation chargerReservation(Integer id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Aucune réservation avec l'identifiant " + id + "."));
    }

    /**
     * Convertit le filtre « statut » reçu en texte.
     *
     * Le paramètre est déclaré String dans le contrôleur, et non
     * StatutReservation : la conversion automatique de Spring échouerait sur
     * une valeur inconnue par une exception générique, là où l'appelant gagne
     * à lire la liste des valeurs acceptées.
     */
    private StatutReservation convertirStatut(String statutDemande) {
        if (statutDemande == null || statutDemande.trim().isEmpty()) {
            return null;
        }
        try {
            return StatutReservation.valueOf(statutDemande.trim().toUpperCase());
        } catch (IllegalArgumentException erreurDeConversion) {
            throw new BadRequestException(
                    "Statut « " + statutDemande + " » inconnu. Valeurs acceptées : "
                            + StatutReservation.valeursAcceptees() + ".");
        }
    }

    private List<ReservationResponseDTO> versDTO(List<Reservation> reservations) {
        return reservations.stream().map(this::versDTO).collect(Collectors.toList());
    }

    private ReservationResponseDTO versDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getReservationId());
        dto.setLivreId(reservation.getLivre().getBookId());
        dto.setLivreTitre(reservation.getLivre().getBookName());
        dto.setAdherentId(reservation.getAdherent().getUserId());
        dto.setAdherentNom(reservation.getAdherent().getName());
        dto.setDateReservation(reservation.getDateReservation());
        dto.setDateExpiration(reservation.getDateExpiration());
        dto.setStatut(reservation.getStatut());
        return dto;
    }
}
