package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dto.ReservationRequestDTO;
import com.ibizabroker.bibliotheque.dto.ReservationResponseDTO;
import com.ibizabroker.bibliotheque.security.UtilisateurAuthentifie;
import com.ibizabroker.bibliotheque.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Les cinq routes du module Réservation, plus une sixième en bonus.
 *
 * Ce contrôleur ne contient aucune règle de gestion : il traduit une requête
 * HTTP en appel de service et un résultat en code de retour. Tout ce qui
 * ressemble à une décision — disponibilité du livre, plafond de réservations,
 * transition de statut — appartient à ReservationService.
 *
 * Les codes d'erreur (400, 404, 409) ne sont pas produits ici non plus : ils
 * viennent des exceptions du service, traduites par
 * ReservationExceptionHandler.
 *
 * SÉCURITÉ (séance 4)
 * Toutes les routes exigent un token (RS-01) : c'est WebSecurityConfiguration
 * qui l'impose, aucune méthode ici n'est atteinte par un anonyme. Le
 * demandeur arrive par @AuthenticationPrincipal, sous la forme de
 * l'UtilisateurAuthentifie que JwtService a construit depuis le token, et
 * c'est lui — jamais le corps de la requête — que le service consulte pour
 * savoir qui parle (RS-04).
 *
 * Les seules décisions prises ici sont celles qui ne dépendent que du rôle,
 * par @PreAuthorize (RS-02). Celles qui dépendent de la réservation visée,
 * « est-elle à lui ? », exigent de l'avoir chargée : elles sont dans le
 * service (RS-03, RS-05).
 */
@CrossOrigin("http://localhost:4200/")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Crée une réservation. 201 avec l'en-tête Location de la ressource créée.
     *
     * ADHERENT : pour lui-même uniquement, l'adherentId du corps est ignoré
     * ou refusé s'il désigne quelqu'un d'autre (RS-04). BIBLIOTHECAIRE : au
     * nom de n'importe qui.
     *
     * required = false sur le corps : sans cela, Spring rejetterait une requête
     * sans corps par un 400 muet. On préfère laisser le service répondre en
     * nommant les champs attendus.
     */
    @PostMapping
    public ResponseEntity<ReservationResponseDTO> creer(
            @RequestBody(required = false) ReservationRequestDTO demande,
            @AuthenticationPrincipal UtilisateurAuthentifie demandeur) {

        ReservationResponseDTO reservation = reservationService.creer(demande, demandeur);

        URI localisation = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reservation.getId())
                .toUri();

        return ResponseEntity.created(localisation).body(reservation);
    }

    /**
     * Liste les réservations. Les deux filtres sont facultatifs et cumulables.
     * Un ADHERENT n'obtient que les siennes (RS-05), un BIBLIOTHECAIRE toutes.
     */
    @GetMapping
    public List<ReservationResponseDTO> lister(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Integer adherentId,
            @AuthenticationPrincipal UtilisateurAuthentifie demandeur) {

        return reservationService.lister(statut, adherentId, demandeur);
    }

    /**
     * Bonus : les réservations expirées, tous adhérents confondus.
     *
     * Une vue transversale sur l'ensemble des adhérents : réservée au
     * bibliothécaire, comme la liste complète.
     *
     * Déclarée avant /{id} par souci de lisibilité ; l'ordre n'a pas d'incidence,
     * Spring donne toujours la priorité au chemin littéral sur le gabarit.
     */
    @GetMapping("/expirees")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public List<ReservationResponseDTO> listerExpirees() {
        return reservationService.listerExpirees();
    }

    /** Consulte une réservation ; la sienne pour un ADHERENT (RS-03). */
    @GetMapping("/{id}")
    public ReservationResponseDTO consulter(@PathVariable Integer id,
                                            @AuthenticationPrincipal UtilisateurAuthentifie demandeur) {
        return reservationService.consulter(id, demandeur);
    }

    /**
     * Annule une réservation ; la sienne pour un ADHERENT (RS-03).
     * PATCH et non PUT : on modifie un seul champ, le statut, sans remplacer
     * la ressource.
     */
    @PatchMapping("/{id}/annuler")
    public ReservationResponseDTO annuler(@PathVariable Integer id,
                                          @AuthenticationPrincipal UtilisateurAuthentifie demandeur) {
        return reservationService.annuler(id, demandeur);
    }

    /**
     * Supprime définitivement la ligne. 204 : succès sans corps.
     *
     * BIBLIOTHECAIRE uniquement (RS-02). La règle est écrite deux fois à
     * dessein : ici, au plus près de la méthode, et dans
     * WebSecurityConfiguration, qui arrête la requête avant même d'atteindre
     * le contrôleur. Un adhérent reçoit 403 dans les deux cas.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public ResponseEntity<Void> supprimer(@PathVariable Integer id) {
        reservationService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
