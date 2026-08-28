import { HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DemandeReservation, Reservation, StatutReservation } from '../_model/reservation';
import { ApiService } from './api.service';

/**
 * Le seul point de contact entre l'écran et /api/reservations.
 *
 * Aucun composant n'injecte HttpClient : ils appellent ce service et ne
 * connaissent ni les URL, ni les verbes, ni la forme du corps d'erreur. Changer
 * l'adresse du backend ou le nom d'un paramètre ne touche que ce fichier.
 */
@Injectable({
  providedIn: 'root'
})
export class ReservationService {

  private readonly basePath = '/api/reservations';

  constructor(private api: ApiService) { }

  /**
   * GET /api/reservations, avec les deux filtres facultatifs du contrôleur.
   *
   * Le filtrage par statut est délégué au serveur plutôt que fait en mémoire :
   * c'est lui qui détient la liste complète, et l'écran resterait juste même
   * si les réservations se comptaient en milliers.
   */
  lister(statut?: StatutReservation | '', adherentId?: number): Observable<Reservation[]> {
    let parametres = new HttpParams();

    if (statut) {
      parametres = parametres.set('statut', statut);
    }
    if (adherentId !== undefined && adherentId !== null) {
      parametres = parametres.set('adherentId', adherentId);
    }

    return this.api.get<Reservation[]>(this.basePath, { params: parametres });
  }

  /** POST /api/reservations. 201 en cas de succès, 400/404/409 sinon. */
  creer(demande: DemandeReservation): Observable<Reservation> {
    return this.api.post<Reservation>(this.basePath, demande);
  }

  /**
   * PATCH /api/reservations/{id}/annuler.
   *
   * Corps vide mais présent : sans second argument, HttpClient n'enverrait pas
   * de Content-Type et certains intermédiaires refusent un PATCH sans corps.
   */
  annuler(id: number): Observable<Reservation> {
    return this.api.patch<Reservation>(`${this.basePath}/${id}/annuler`, {});
  }

  /**
   * Traduit une erreur HTTP en phrase affichable.
   *
   * Placé ici et non dans les composants pour une raison simple : le message
   * qui compte est celui du serveur — « RG-03 : A2 a déjà 3 réservations
   * actives… » — et savoir qu'il se trouve dans « error.message » du corps
   * ApiError relève de la connaissance de l'API, donc de ce service.
   *
   * Les repli par code ne servent qu'aux réponses sans corps exploitable :
   * jamais un « une erreur est survenue » quand le serveur a dit mieux.
   */
  messageDErreur(erreur: unknown): string {
    if (!(erreur instanceof HttpErrorResponse)) {
      return "Anomalie inattendue de l'application. Rechargez l'écran, puis réessayez.";
    }

    // status 0 : la requête n'est jamais partie (backend arrêté, CORS, réseau).
    if (erreur.status === 0) {
      return 'Le serveur est injoignable : aucune réponse de ' + this.api.url(this.basePath)
        + '. Vérifiez que le backend est démarré, puis réessayez.';
    }

    const messageDuServeur = this.extraireMessage(erreur.error);
    if (messageDuServeur) {
      return messageDuServeur;
    }

    switch (erreur.status) {
      case 400:
        return 'Requête refusée : les champs « livre » et « adhérent » sont tous deux obligatoires.';
      case 401:
        return 'Votre session a expiré. Reconnectez-vous, puis réessayez.';
      case 403:
        return "Vous n'avez pas les droits nécessaires pour cette opération.";
      case 404:
        return "La ressource demandée n'existe pas ou plus.";
      case 409:
        return "L'opération est refusée par une règle de gestion du serveur.";
      default:
        return 'Le serveur a répondu ' + erreur.status
          + (erreur.statusText ? ' (' + erreur.statusText + ')' : '') + '.';
    }
  }

  /**
   * Ligne technique d'un échec : « HTTP 409 · Conflit · RG-01 ».
   *
   * Elle accompagne le message du serveur sans le remplacer. Deux refus
   * peuvent se ressembler à la lecture — un livre indisponible et un quota
   * atteint sont tous deux des 409 — et c'est la règle citée qui les sépare.
   * L'afficher évite d'avoir à ouvrir l'onglet réseau pour savoir laquelle a
   * parlé.
   */
  detailDErreur(erreur: unknown): string {
    if (!(erreur instanceof HttpErrorResponse)) {
      return 'Erreur applicative · hors réponse HTTP';
    }

    if (erreur.status === 0) {
      return 'Aucune réponse · serveur injoignable';
    }

    const libelles: { [code: number]: string } = {
      400: 'Requête invalide',
      401: 'Non authentifié',
      403: 'Accès refusé',
      404: 'Introuvable',
      409: 'Conflit'
    };

    let ligne = 'HTTP ' + erreur.status;

    const libelle = libelles[erreur.status] ?? erreur.statusText;
    if (libelle) {
      ligne += ' · ' + libelle;
    }

    // Les messages du module nomment leur règle : « RG-01 : le livre … ».
    // La reprendre ici met le cas en évidence d'un coup d'œil.
    const regle = /\bRG-\d{2}\b/.exec(this.messageDErreur(erreur));
    if (regle) {
      ligne += ' · ' + regle[0];
    }

    return ligne;
  }

  /**
   * Récupère le champ « message » d'un corps ApiError.
   *
   * Le corps n'est pas toujours du JSON : une erreur d'authentification
   * remonte en texte brut, et un intermédiaire peut renvoyer une page HTML.
   * On écarte ce dernier cas, illisible dans une alerte.
   */
  private extraireMessage(corps: unknown): string | null {
    if (typeof corps === 'string') {
      const texte = corps.trim();
      return texte && !texte.startsWith('<') ? texte : null;
    }

    if (corps && typeof corps === 'object') {
      const message = (corps as { message?: unknown }).message;
      if (typeof message === 'string' && message.trim()) {
        return message.trim();
      }
    }

    return null;
  }
}
