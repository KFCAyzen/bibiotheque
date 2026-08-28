import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import {
  Reservation,
  STATUTS,
  STATUTS_ACTIFS,
  StatutReservation
} from '../../_model/reservation';

/**
 * Composant de présentation : le tableau des réservations et ses quatre états.
 *
 * Il n'appelle rien et ne décide rien. Tout ce qu'il affiche arrive par
 * @Input ; tout ce que l'utilisateur demande repart par @Output vers le
 * conteneur, seul détenteur de l'état et des appels HTTP.
 */
@Component({
  selector: 'app-reservation-list',
  templateUrl: './reservation-list.component.html',
  styleUrls: ['./reservation-list.component.css']
})
export class ReservationListComponent implements OnChanges {

  @Input() reservations: Reservation[] = [];

  /** État « chargement » : l'appel est parti, la réponse n'est pas arrivée. */
  @Input() chargement = false;

  /** État « erreur » : message déjà rendu lisible par ReservationService. */
  @Input() erreur: string | null = null;

  @Input() filtreStatut: StatutReservation | '' = '';

  /** Identifiant de la ligne dont l'annulation est en cours, pour figer son bouton. */
  @Input() idEnCoursAnnulation: number | null = null;

  /**
   * Ligne technique de l'échec de lecture, à côté du message du serveur.
   * Les refus d'annulation, eux, partent en notification.
   */
  @Input() detailErreur: string | null = null;

  @Output() filtreStatutChange = new EventEmitter<StatutReservation | ''>();

  @Output() reessayer = new EventEmitter<void>();

  @Output() annulationDemandee = new EventEmitter<Reservation>();

  readonly statuts = STATUTS;

  /**
   * Les crans de la jauge de validité. Sept, comme les sept jours de RG-04 :
   * un cran par jour, pour que la longueur de la barre se lise en jours et
   * pas en pourcentage.
   */
  readonly crans = [0, 1, 2, 3, 4, 5, 6];

  /**
   * Copie triée de l'entrée. On ne trie jamais le tableau reçu : il appartient
   * au conteneur, et une mutation ici lui ferait voir un ordre qu'il n'a pas
   * demandé.
   */
  reservationsAffichees: Reservation[] = [];

  /** Tri sur la date d'expiration, la plus proche en tête par défaut. */
  triCroissant = true;

  ngOnChanges(changements: SimpleChanges): void {
    if (changements['reservations']) {
      this.trier();
    }
  }

  basculerLeTri(): void {
    this.triCroissant = !this.triCroissant;
    this.trier();
  }

  /** RG-05 : seuls EN_ATTENTE et DISPONIBLE peuvent encore être annulés. */
  estAnnulable(reservation: Reservation): boolean {
    return STATUTS_ACTIFS.indexOf(reservation.statut) !== -1;
  }

  libelleDuStatut(statut: StatutReservation): string {
    return this.definition(statut).libelle;
  }

  /** Classe d'encre du tampon de statut : « tampon-attente », « tampon-refus »… */
  encreDuStatut(statut: StatutReservation): string {
    return 'tampon-' + this.definition(statut).encre;
  }

  /**
   * La jauge de validité ne se montre que sur une réservation encore vivante.
   * Sur une réservation annulée, expirée ou honorée, la fenêtre ne court plus :
   * y afficher un décompte serait faux.
   */
  afficheLaJauge(reservation: Reservation): boolean {
    return this.estAnnulable(reservation);
  }

  /**
   * Crans consommés sur la fenêtre de validité.
   *
   * La fenêtre n'est pas supposée durer sept jours : on la mesure entre les
   * deux dates que le serveur a réellement écrites. Si RG-04 change de délai,
   * la jauge suit sans qu'on y touche.
   */
  cransConsommes(reservation: Reservation): number {
    const debut = Date.parse(reservation.dateReservation);
    const fin = Date.parse(reservation.dateExpiration);
    if (!(fin > debut)) {
      return this.crans.length;
    }

    const part = (Date.now() - debut) / (fin - debut);
    const consommes = Math.floor(part * this.crans.length);
    // Une réservation qui vient de naître garde ses sept crans vides ; une
    // réservation dépassée les a tous pleins.
    return Math.min(this.crans.length, Math.max(0, consommes));
  }

  /** Jours entiers restants avant expiration, jamais négatif. */
  joursRestants(reservation: Reservation): number {
    const restant = Date.parse(reservation.dateExpiration) - Date.now();
    return Math.max(0, Math.ceil(restant / 86400000));
  }

  /** Dernier jour : c'est là que le guichet doit relancer l'adhérent. */
  estUrgent(reservation: Reservation): boolean {
    return this.joursRestants(reservation) <= 1;
  }

  /** *ngFor sur les lignes : évite de reconstruire le tableau à chaque refus. */
  identifiant(_index: number, reservation: Reservation): number {
    return reservation.id;
  }

  private definition(statut: StatutReservation) {
    const trouvee = this.statuts.find(candidat => candidat.valeur === statut);
    // Repli défensif : un statut ajouté côté serveur et pas ici resterait
    // affichable, à l'encre éteinte, au lieu de faire planter le gabarit.
    return trouvee ?? { valeur: statut, libelle: statut, encre: 'passe' as const };
  }

  private trier(): void {
    // Les dates arrivent en ISO-8601 : leur ordre lexicographique est déjà
    // l'ordre chronologique, aucune conversion en Date n'est nécessaire.
    this.reservationsAffichees = (this.reservations ?? []).slice().sort((a, b) => {
      const comparaison = a.dateExpiration.localeCompare(b.dateExpiration);
      return this.triCroissant ? comparaison : -comparaison;
    });
  }
}
