/**
 * Reflet exact de ReservationResponseDTO, le seul contrat que le backend
 * expose sur /api/reservations. Les noms de champs sont ceux du JSON : les
 * renommer ici obligerait à transformer chaque réponse pour rien.
 */

/**
 * Les cinq valeurs de l'énumération StatutReservation du backend.
 *
 * Déclaré en union de littéraux plutôt qu'en enum TypeScript : la valeur qui
 * circule sur le réseau est déjà une chaîne, et strictTemplates vérifie alors
 * qu'aucun gabarit ne compare un statut à une chaîne qui n'existe pas.
 */
export type StatutReservation =
  | 'EN_ATTENTE'
  | 'DISPONIBLE'
  | 'ANNULEE'
  | 'EXPIREE'
  | 'HONOREE';

export interface Reservation {
  id: number;
  livreId: number;
  livreTitre: string;
  adherentId: number;
  adherentNom: string;
  /** ISO-8601 tel que sérialisé par Jackson, converti à l'affichage par DatePipe. */
  dateReservation: string;
  dateExpiration: string;
  statut: StatutReservation;
}

/** Reflet de ReservationRequestDTO : les deux seuls champs acceptés au POST. */
export interface DemandeReservation {
  livreId: number;
  adherentId: number;
}

/**
 * Corps d'erreur ApiError renvoyé par ReservationExceptionHandler pour tout
 * 400, 404 et 409 du module. C'est « message » que l'écran doit afficher :
 * il porte la règle enfreinte, là où le code HTTP seul ne dirait rien.
 */
export interface ApiError {
  horodatage: string;
  statut: number;
  erreur: string;
  message: string;
}

/**
 * Les statuts tels que présentés dans le filtre et dans la colonne « statut ».
 *
 * Ce n'est pas de la donnée métier codée en dur : c'est la traduction d'une
 * énumération fermée, connue à la compilation des deux côtés. Les réservations,
 * les livres et les adhérents, eux, viennent tous de l'API.
 */
export const STATUTS: ReadonlyArray<{
  valeur: StatutReservation;
  libelle: string;
  /**
   * Couleur d'encre du tampon, suffixe de la classe « tampon-… ».
   *
   * Quatre encres pour cinq statuts, parce que le guichet n'en distingue que
   * quatre : ce qui attend, ce qui est prêt, ce qui a été refusé, et ce qui
   * est clos. Un statut clos — expiré ou honoré — n'appelle plus d'action :
   * son tampon a passé.
   */
  encre: 'attente' | 'disponible' | 'refus' | 'passe';
}> = [
  { valeur: 'EN_ATTENTE', libelle: 'En attente', encre: 'attente' },
  { valeur: 'DISPONIBLE', libelle: 'Disponible', encre: 'disponible' },
  { valeur: 'ANNULEE', libelle: 'Annulée', encre: 'refus' },
  { valeur: 'EXPIREE', libelle: 'Expirée', encre: 'passe' },
  { valeur: 'HONOREE', libelle: 'Honorée', encre: 'passe' },
];

/** Les deux statuts depuis lesquels RG-05 autorise encore une annulation. */
export const STATUTS_ACTIFS: ReadonlyArray<StatutReservation> = ['EN_ATTENTE', 'DISPONIBLE'];
