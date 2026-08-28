import { Component, OnInit, ViewChild } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Books } from '../_model/books';
import {
  DemandeReservation,
  Reservation,
  STATUTS_ACTIFS,
  StatutReservation
} from '../_model/reservation';
import { Users } from '../_model/users';
import { BooksService } from '../_service/books.service';
import { ReservationService } from '../_service/reservation.service';
import { UsersService } from '../_service/users.service';
import { NotificationService } from '../_service/notification.service';
import { ReservationFormComponent } from './reservation-form/reservation-form.component';

/**
 * Conteneur de l'écran de gestion des réservations.
 *
 * Il est le seul à parler aux services, donc le seul à détenir l'état :
 * chargement en cours, données, liste vide, erreur. Les deux composants
 * enfants ne reçoivent que le résultat de ces décisions et lui renvoient les
 * intentions de l'utilisateur.
 *
 * Aucun HttpClient n'est injecté ici : les appels passent par
 * ReservationService pour le module Réservation, et par les services déjà
 * présents dans le projet — BooksService et UsersService — pour alimenter les
 * deux listes déroulantes.
 */
@Component({
  selector: 'app-reservations',
  templateUrl: './reservations.component.html',
  styleUrls: ['./reservations.component.css']
})
export class ReservationsComponent implements OnInit {

  /** Accès au formulaire pour le vider après une création réussie. */
  @ViewChild(ReservationFormComponent) formulaire?: ReservationFormComponent;

  // --- La liste ------------------------------------------------------------
  reservations: Reservation[] = [];
  chargement = false;
  erreurChargement: string | null = null;
  detailChargement: string | null = null;
  filtreStatut: StatutReservation | '' = '';

  // --- Les listes déroulantes ----------------------------------------------
  livres: Books[] = [];
  adherents: Users[] = [];
  chargementReferences = false;
  erreurReferences: string | null = null;
  detailReferences: string | null = null;

  // --- La création ---------------------------------------------------------
  creationEnCours = false;
  reservationsActives: number | null = null;

  /**
   * Le compteur de RG-03 n'a pas pu être lu. Ce n'est pas un échec de
   * l'opération — la création reste possible — mais le taire laisserait un
   * blanc que l'utilisateur prendrait pour « zéro réservation active ».
   */
  compteurIndisponible = false;

  // --- L'annulation --------------------------------------------------------
  reservationAAnnuler: Reservation | null = null;
  idEnCoursAnnulation: number | null = null;

  constructor(
    private reservationService: ReservationService,
    private booksService: BooksService,
    private usersService: UsersService,
    private notifications: NotificationService
  ) { }

  ngOnInit(): void {
    this.chargerLesReservations();
    this.chargerLesReferences();
  }

  // =========================================================================
  // Liste
  // =========================================================================

  chargerLesReservations(): void {
    this.chargement = true;
    this.erreurChargement = null;
    this.detailChargement = null;

    this.reservationService.lister(this.filtreStatut).subscribe({
      next: reservations => {
        this.reservations = reservations;
        this.chargement = false;
      },
      error: erreur => {
        // La liste précédente est effacée : garder à l'écran des données dont
        // on ne sait plus si elles sont à jour serait pire que rien.
        this.reservations = [];
        this.erreurChargement = this.reservationService.messageDErreur(erreur);
        this.detailChargement = this.reservationService.detailDErreur(erreur);
        this.chargement = false;
      }
    });
  }

  surChangementDeFiltre(statut: StatutReservation | ''): void {
    this.filtreStatut = statut;
    this.chargerLesReservations();
  }

  // =========================================================================
  // Listes déroulantes
  // =========================================================================

  /**
   * Les livres et les adhérents sont chargés ensemble : le formulaire n'est
   * utilisable qu'avec les deux, et forkJoin échoue au premier des deux appels
   * qui échoue — c'est exactement la condition d'affichage du message.
   */
  chargerLesReferences(): void {
    this.chargementReferences = true;
    this.erreurReferences = null;
    this.detailReferences = null;

    forkJoin({
      livres: this.booksService.getBooksList(),
      adherents: this.usersService.getUsersList()
    }).subscribe({
      next: reponses => {
        this.livres = reponses.livres;
        this.adherents = reponses.adherents;
        this.chargementReferences = false;
      },
      error: erreur => {
        this.livres = [];
        this.adherents = [];
        this.erreurReferences = "Les livres et les adhérents n'ont pas pu être chargés, "
          + "le formulaire reste donc inutilisable. "
          + this.reservationService.messageDErreur(erreur);
        this.detailReferences = this.reservationService.detailDErreur(erreur);
        this.chargementReferences = false;
      }
    });
  }

  // =========================================================================
  // Création
  // =========================================================================

  creerLaReservation(demande: DemandeReservation): void {
    this.creationEnCours = true;

    this.reservationService.creer(demande).subscribe({
      next: reservation => {
        this.creationEnCours = false;

        let message = 'Fiche n° ' + reservation.id + ' ouverte pour « '
          + reservation.livreTitre + ' » au nom de ' + reservation.adherentNom
          + '. Elle expire le '
          + this.enDateCourte(reservation.dateExpiration) + '.';

        // Le filtre courant masquerait la ligne qui vient d'être créée :
        // on le remet sur « Tous » pour que l'utilisateur la voie apparaître.
        if (this.filtreStatut && this.filtreStatut !== reservation.statut) {
          this.filtreStatut = '';
          message += " Le filtre a été remis sur « Tous » pour l'afficher.";
        }

        this.notifications.succes(
          'Réservation enregistrée',
          message,
          'HTTP 201 · Créée · statut ' + reservation.statut
        );

        this.formulaire?.reinitialiser();
        this.chargerLesReservations();
      },
      error: erreur => {
        this.creationEnCours = false;
        this.notifications.refus(
          'Réservation refusée',
          this.messageDeCreation(erreur),
          this.reservationService.detailDErreur(erreur)
        );
      }
    });
  }

  /** « 04/09/2026 » à partir de l'ISO-8601 renvoyé par le serveur. */
  private enDateCourte(iso: string): string {
    const date = new Date(iso);
    return isNaN(date.getTime()) ? iso : date.toLocaleDateString('fr-FR');
  }

  /**
   * Un 404 sur la création n'est pas une faute de saisie : l'utilisateur a
   * choisi dans une liste, donc c'est cette liste qui est périmée. Le message
   * du serveur reste affiché, complété par ce qui vient d'être fait.
   */
  private messageDeCreation(erreur: unknown): string {
    const message = this.reservationService.messageDErreur(erreur);

    if (this.codeHttp(erreur) === 404) {
      this.chargerLesReferences();
      return message + " Le livre ou l'adhérent choisi n'existe plus : "
        + "les listes déroulantes viennent d'être rechargées.";
    }

    return message;
  }

  private codeHttp(erreur: unknown): number | null {
    const statut = (erreur as { status?: unknown })?.status;
    return typeof statut === 'number' ? statut : null;
  }

  /**
   * Compteur de réservations actives de l'adhérent choisi (RG-03).
   *
   * Un appel dédié, filtré sur l'adhérent : la liste affichée peut être
   * restreinte à un statut, la compter donnerait un total faux.
   */
  surSelectionDAdherent(adherentId: number | null): void {
    this.reservationsActives = null;
    this.compteurIndisponible = false;

    if (adherentId === null) {
      return;
    }

    this.reservationService.lister('', adherentId).subscribe({
      next: reservations => {
        this.reservationsActives = reservations
          .filter(reservation => STATUTS_ACTIFS.indexOf(reservation.statut) !== -1)
          .length;
      },
      // Indicateur d'appoint : son échec n'interrompt pas l'utilisateur par une
      // notification, le serveur restant seul juge au moment de la création.
      // Mais il se dit, sans quoi l'absence de compteur se lirait « zéro ».
      error: () => {
        this.reservationsActives = null;
        this.compteurIndisponible = true;
      }
    });
  }

  // =========================================================================
  // Annulation
  // =========================================================================

  demanderLAnnulation(reservation: Reservation): void {
    this.reservationAAnnuler = reservation;
  }

  renoncerALAnnulation(): void {
    this.reservationAAnnuler = null;
  }

  confirmerLAnnulation(): void {
    const reservation = this.reservationAAnnuler;
    if (reservation === null) {
      return;
    }

    this.idEnCoursAnnulation = reservation.id;

    this.reservationService.annuler(reservation.id).subscribe({
      next: annulee => {
        this.idEnCoursAnnulation = null;
        this.reservationAAnnuler = null;

        this.notifications.succes(
          'Réservation annulée',
          'Fiche n° ' + annulee.id + ' — « ' + annulee.livreTitre + ' » au nom de '
            + annulee.adherentNom + '. Le statut est désormais définitif.',
          'HTTP 200 · ' + reservation.statut + ' → ' + annulee.statut
        );

        this.remplacerDansLaListe(annulee);
      },
      error: erreur => {
        this.idEnCoursAnnulation = null;
        this.reservationAAnnuler = null;

        this.notifications.refus(
          'Annulation refusée',
          this.reservationService.messageDErreur(erreur),
          this.reservationService.detailDErreur(erreur)
        );
      }
    });
  }

  /**
   * Met la ligne à jour à partir de la réponse du serveur, sans recharger la
   * liste : le DTO renvoyé par PATCH est déjà l'état exact de la réservation.
   * Si un filtre est actif et que le nouveau statut n'y répond plus, la ligne
   * quitte l'affichage — c'est le résultat qu'aurait donné un rechargement.
   *
   * Un nouveau tableau, et non une mutation : c'est le changement de référence
   * qui déclenche le ngOnChanges du composant de liste, donc son tri.
   */
  private remplacerDansLaListe(annulee: Reservation): void {
    if (this.filtreStatut && this.filtreStatut !== annulee.statut) {
      this.reservations = this.reservations.filter(existante => existante.id !== annulee.id);
      return;
    }

    this.reservations = this.reservations.map(
      existante => existante.id === annulee.id ? annulee : existante
    );
  }

}
