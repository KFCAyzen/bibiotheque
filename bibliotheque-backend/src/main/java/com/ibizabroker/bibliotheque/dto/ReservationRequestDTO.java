package com.ibizabroker.bibliotheque.dto;

import lombok.Data;

/**
 * DTO d'entrée : tout ce que le client a le droit d'envoyer.
 *
 * Volontairement réduit à deux identifiants. La date de réservation, la date
 * d'expiration et le statut sont déterminés par le serveur ; les recevoir du
 * client permettrait de contourner RG-04 en s'accordant une validité illimitée.
 *
 * Les deux champs sont des Integer et non des int : un champ absent du JSON
 * arrive à null, ce qui est précisément la condition détectée par
 * ReservationService pour répondre 400 en nommant le champ manquant. Un int
 * primitif vaudrait 0 et laisserait passer la requête.
 *
 * Depuis la séance 4, adherentId n'est plus une source de vérité (RS-04) :
 * pour un ADHERENT, l'identité vient du token et ce champ est facultatif —
 * s'il est présent, il doit être le sien, sinon 403. Seul un BIBLIOTHECAIRE,
 * qui réserve au nom d'un adhérent, doit le renseigner.
 */
@Data
public class ReservationRequestDTO {

    private Integer livreId;

    private Integer adherentId;
}
