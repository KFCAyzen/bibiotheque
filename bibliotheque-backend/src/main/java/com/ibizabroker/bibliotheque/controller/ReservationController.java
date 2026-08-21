package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dto.ReservationRequestDTO;
import com.ibizabroker.bibliotheque.dto.ReservationResponseDTO;
import com.ibizabroker.bibliotheque.service.ReservationService;
import org.springframework.http.ResponseEntity;
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
     * required = false sur le corps : sans cela, Spring rejetterait une requête
     * sans corps par un 400 muet. On préfère laisser le service répondre en
     * nommant les champs attendus.
     */
    @PostMapping
    public ResponseEntity<ReservationResponseDTO> creer(
            @RequestBody(required = false) ReservationRequestDTO demande) {

        ReservationResponseDTO reservation = reservationService.creer(demande);

        URI localisation = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reservation.getId())
                .toUri();

        return ResponseEntity.created(localisation).body(reservation);
    }

    /** Liste les réservations. Les deux filtres sont facultatifs et cumulables. */
    @GetMapping
    public List<ReservationResponseDTO> lister(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Integer adherentId) {

        return reservationService.lister(statut, adherentId);
    }

    /**
     * Bonus : les réservations expirées.
     *
     * Déclarée avant /{id} par souci de lisibilité ; l'ordre n'a pas d'incidence,
     * Spring donne toujours la priorité au chemin littéral sur le gabarit.
     */
    @GetMapping("/expirees")
    public List<ReservationResponseDTO> listerExpirees() {
        return reservationService.listerExpirees();
    }

    @GetMapping("/{id}")
    public ReservationResponseDTO consulter(@PathVariable Integer id) {
        return reservationService.consulter(id);
    }

    /**
     * Annule une réservation. PATCH et non PUT : on modifie un seul champ,
     * le statut, sans remplacer la ressource.
     */
    @PatchMapping("/{id}/annuler")
    public ReservationResponseDTO annuler(@PathVariable Integer id) {
        return reservationService.annuler(id);
    }

    /** Supprime définitivement la ligne. 204 : succès sans corps. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Integer id) {
        reservationService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
