import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/**
 * Les deux tons d'une notification.
 *
 * « succes » confirme ce qui vient d'être fait ; « refus » porte le message
 * que le serveur a opposé à la demande. Il n'y a délibérément pas de ton
 * « information » : une notification qui n'annonce ni une réussite ni un refus
 * n'a rien à interrompre.
 */
export type TonNotification = 'succes' | 'refus';

export interface Notification {
  /** Identifiant de pile, sans rapport avec les identifiants métier. */
  id: number;
  ton: TonNotification;
  /** Ce qui s'est passé, en trois mots. */
  titre: string;
  /** Le détail — pour un refus, le message du serveur, mot pour mot. */
  message: string;
  /**
   * Ligne technique : code HTTP, libellé, règle de gestion en cause. Elle
   * lève l'ambiguïté entre deux refus dont les phrases se ressemblent, et
   * rend le cas identifiable sans ouvrir la console.
   */
  detail: string | null;
  /**
   * Durée d'affichage en millisecondes, ou null pour une notification qui
   * attend d'être fermée à la main.
   */
  duree: number | null;
}

/** Une confirmation a le temps d'être lue, puis s'efface. */
const DUREE_SUCCES = 5000;

/**
 * Diffuse les notifications transitoires de l'application.
 *
 * Le service ne connaît ni la pile ni son rendu : il émet, le composant
 * NotificationsComponent écoute et affiche. Cette séparation permet à
 * n'importe quel écran de notifier sans dépendre d'un composant précis.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {

  private readonly sujet = new Subject<Notification>();

  private compteur = 0;

  /** Flux consommé par la pile d'affichage. */
  get flux(): Observable<Notification> {
    return this.sujet.asObservable();
  }

  /** Confirmation d'une action réussie. S'efface seule. */
  succes(titre: string, message: string, detail: string | null = null): void {
    this.emettre('succes', titre, message, detail, DUREE_SUCCES);
  }

  /**
   * Refus opposé par le serveur.
   *
   * Volontairement sans durée : un message de règle métier — RG-01, RG-03,
   * RG-06 — est la seule explication que l'utilisateur recevra de son échec.
   * Le faire disparaître au bout de cinq secondes reviendrait à la lui
   * retirer avant qu'il ait fini de la lire.
   */
  refus(titre: string, message: string, detail: string | null = null): void {
    this.emettre('refus', titre, message, detail, null);
  }

  messageErreurHttp(erreur: any, messageParDefaut: string): string {
    if (erreur?.status === 0) {
      return 'Backend unavailable. Check that the server is running, then try again.';
    }

    return erreur?.error?.message
      || (typeof erreur?.error === 'string' ? erreur.error : null)
      || messageParDefaut;
  }

  detailErreurHttp(erreur: any): string | null {
    if (erreur?.status === 0) {
      return 'No response from backend';
    }

    return erreur?.status ? `HTTP ${erreur.status}` : null;
  }

  private emettre(
    ton: TonNotification,
    titre: string,
    message: string,
    detail: string | null,
    duree: number | null
  ): void {
    this.compteur += 1;
    this.sujet.next({ id: this.compteur, ton, titre, message, detail, duree });
  }
}
