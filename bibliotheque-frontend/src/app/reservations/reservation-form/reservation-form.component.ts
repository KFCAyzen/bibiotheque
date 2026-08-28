import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Books } from '../../_model/books';
import { DemandeReservation } from '../../_model/reservation';
import { Users } from '../../_model/users';

/** Seuil de RG-03, affiché à titre d'avertissement avant même l'envoi. */
const PLAFOND_RESERVATIONS_ACTIVES = 3;

/**
 * Composant de présentation : le formulaire de création.
 *
 * Les deux listes déroulantes sont alimentées par le conteneur, qui les tient
 * de /admin/books et /admin/users. Aucun identifiant n'est saisi à la main, et
 * aucune valeur n'est écrite en dur dans ce fichier.
 */
@Component({
  selector: 'app-reservation-form',
  templateUrl: './reservation-form.component.html',
  styleUrls: ['./reservation-form.component.css']
})
export class ReservationFormComponent {

  @Input() livres: Books[] = [];

  @Input() adherents: Users[] = [];

  @Input() chargementReferences = false;

  @Input() erreurReferences: string | null = null;

  /**
   * Ligne technique de l'échec de chargement des listes déroulantes.
   * Les refus métier de la création, eux, partent en notification.
   */
  @Input() detailReferences: string | null = null;

  @Input() creationEnCours = false;

  /** Le compteur de RG-03 n'a pas pu être lu : on le dit plutôt que rien. */
  @Input() compteurIndisponible = false;

  /** Réservations actives de l'adhérent sélectionné ; null si inconnu. */
  @Input() reservationsActives: number | null = null;

  @Output() creer = new EventEmitter<DemandeReservation>();

  @Output() rechargerReferences = new EventEmitter<void>();

  @Output() adherentSelectionne = new EventEmitter<number | null>();

  readonly plafond = PLAFOND_RESERVATIONS_ACTIVES;

  /**
   * Les crans du quota, un par réservation que RG-03 autorise. Au guichet la
   * question n'est pas « combien en a-t-il » mais « lui en reste-t-il » :
   * trois crans y répondent sans avoir à lire la phrase.
   */
  readonly crans = Array.from({ length: PLAFOND_RESERVATIONS_ACTIVES }, (_, i) => i);

  livreId: number | null = null;

  adherentId: number | null = null;

  /**
   * Validation avant envoi : tant qu'elle est fausse, le bouton reste inactif.
   * Elle ne remplace pas les contrôles du serveur, elle évite seulement de lui
   * envoyer une requête dont on sait déjà qu'elle sera refusée par un 400.
   */
  get formulaireValide(): boolean {
    return this.livreId !== null && this.adherentId !== null;
  }

  surChangementDAdherent(): void {
    this.adherentSelectionne.emit(this.adherentId);
  }

  soumettre(): void {
    if (!this.formulaireValide || this.creationEnCours) {
      return;
    }

    this.creer.emit({
      livreId: this.livreId as number,
      adherentId: this.adherentId as number
    });
  }

  /** Appelé par le conteneur après une création réussie. */
  reinitialiser(): void {
    this.livreId = null;
    this.adherentId = null;
    this.adherentSelectionne.emit(null);
  }
}
