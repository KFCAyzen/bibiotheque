import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { Notification, NotificationService } from '../_service/notification.service';

/**
 * La pile de notifications de l'application.
 *
 * Posée une seule fois dans app.component.html, elle survit aux changements de
 * route : une confirmation déclenchée par un écran reste lisible même si
 * l'utilisateur en change aussitôt.
 *
 * Le composant ne décide de rien — ni du ton, ni de la durée, ni du texte.
 * Il empile ce que NotificationService émet et retire ce qui a expiré.
 */
@Component({
  selector: 'app-notifications',
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css']
})
export class NotificationsComponent implements OnInit, OnDestroy {

  /** Les plus récentes en bas, contre le bord : c'est là que l'œil revient. */
  notifications: Notification[] = [];

  /**
   * Au-delà, la pile masquerait le contenu au lieu de le commenter. Les plus
   * anciennes cèdent la place — jamais les plus récentes, qui décrivent ce que
   * l'utilisateur vient de faire.
   */
  private readonly maximum = 4;

  private abonnement?: Subscription;

  /** Un minuteur par notification à durée limitée, pour pouvoir l'annuler. */
  private readonly minuteurs = new Map<number, ReturnType<typeof setTimeout>>();

  constructor(private notificationService: NotificationService) { }

  ngOnInit(): void {
    this.abonnement = this.notificationService.flux.subscribe(
      notification => this.empiler(notification)
    );
  }

  ngOnDestroy(): void {
    this.abonnement?.unsubscribe();
    this.minuteurs.forEach(minuteur => clearTimeout(minuteur));
    this.minuteurs.clear();
  }

  fermer(notification: Notification): void {
    const minuteur = this.minuteurs.get(notification.id);
    if (minuteur !== undefined) {
      clearTimeout(minuteur);
      this.minuteurs.delete(notification.id);
    }

    this.notifications = this.notifications.filter(
      candidate => candidate.id !== notification.id
    );
  }

  /** Les lecteurs d'écran n'annoncent pas un refus comme une confirmation. */
  roleAria(notification: Notification): string {
    return notification.ton === 'refus' ? 'alert' : 'status';
  }

  /** *ngFor sur la pile : sans cela, chaque ajout rejouerait toutes les entrées. */
  identifiant(_index: number, notification: Notification): number {
    return notification.id;
  }

  private empiler(notification: Notification): void {
    this.notifications = [...this.notifications, notification];

    while (this.notifications.length > this.maximum) {
      this.fermer(this.notifications[0]);
    }

    if (notification.duree !== null) {
      this.minuteurs.set(
        notification.id,
        setTimeout(() => this.fermer(notification), notification.duree)
      );
    }
  }
}
