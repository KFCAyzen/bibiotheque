# Séance 4 — Sécuriser et tester le module Réservation

> Contenu prêt à coller dans la description de la Pull Request
> `feature/reservation-securite-kepseu-franck` → `main`.

L'API de réservation de la séance 2 acceptait n'importe quel porteur de jeton
sur n'importe quelle réservation : un adhérent pouvait lister, consulter,
annuler ou supprimer celles des autres, et réserver en leur nom en glissant
leur identifiant dans le corps de la requête.

Cette séance ferme l'API selon la grille de l'énoncé, puis le prouve par des
tests : unitaires sans aucune base, d'intégration contre le PostgreSQL du
projet — les deux par `mvn test`.

`pom.xml` et `application.properties` restent intacts.

---

## Résultat des tests

![Résultat de mvn test](screenshots/seance-4-tests.png)

```
[INFO] Tests run:  1, Failures: 0, Errors: 0, Skipped: 0 - in BibliothequeApplicationTests
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 - in ReservationControllerSecuriteTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0 - in ReservationServiceTest
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Les tests d'intégration visent le conteneur `db` du projet (profil `test`,
`src/test/resources/application-test.properties`) : il doit tourner.

```bash
docker compose up -d db
cd bibliotheque-backend
./mvnw test                      # avec un JDK 8 local
```

ou, sans JDK 8, dans l'image de build du projet, attachée au réseau compose :

```bash
docker run --rm --network bibiotheque_default -e POSTGRES_HOST=db \
  -v "$(pwd):/build" -v "$HOME/.m2:/root/.m2" -w /build \
  maven:3.8-eclipse-temurin-8 mvn -B test
```

---

## Les rôles

Le projet possède depuis la séance 1 une table `ROLE` à deux lignes, `User`
et `Admin`, que le front Angular lit dans la réponse d'authentification pour
choisir sa page d'accueil. Plutôt que de les renommer, `RoleMetier` leur donne
leur sens : un `User` est un **ADHERENT**, un `Admin` un **BIBLIOTHECAIRE**.

À la connexion, `JwtService` attribue à chaque compte ses deux autorités —
l'historique `ROLE_User` / `ROLE_Admin`, que les contrôleurs de la séance 1
consultent toujours, et `ROLE_ADHERENT` / `ROLE_BIBLIOTHECAIRE`, que le module
Réservation utilise. Rien ne change pour les routes existantes ni pour le front.

| Endpoint | Anonyme | ADHERENT | BIBLIOTHECAIRE |
|---|---|---|---|
| `POST /api/reservations` | 401 | pour lui-même uniquement | pour n'importe qui |
| `GET /api/reservations` | 401 | ses réservations seulement | toutes |
| `GET /api/reservations/{id}` | 401 | si elle lui appartient, sinon 403 | toutes |
| `PATCH /api/reservations/{id}/annuler` | 401 | si elle lui appartient, sinon 403 | toutes |
| `DELETE /api/reservations/{id}` | 401 | 403 | oui |
| `GET /api/reservations/expirees` (bonus) | 401 | 403 | oui |

---

## Où chaque règle de sécurité est implémentée

| Règle | Implémentation |
|---|---|
| **RS-01** — sans token, 401 | `WebSecurityConfiguration.configure` : `antMatchers("/api/reservations/**").authenticated()`. `JwtRequestFilter` laisse la requête anonyme si le jeton est absent, expiré ou illisible, et `JwtAuthenticationEntryPoint` répond 401 avec un `ApiError` dont le message dit lequel des trois. |
| **RS-02** — action réservée au bibliothécaire, 403 | `@PreAuthorize("hasRole('BIBLIOTHECAIRE')")` sur `ReservationController.supprimer` et `listerExpirees`, doublé pour DELETE par `antMatchers(HttpMethod.DELETE, "/api/reservations/**").hasRole("BIBLIOTHECAIRE")` dans `WebSecurityConfiguration`. Le 403 est rendu par `AccesInterditHandler` (règle d'URL) ou `ReservationExceptionHandler.roleInsuffisant` (`@PreAuthorize`). |
| **RS-03** — la réservation d'un autre, 403 | `ReservationService.verifierPropriete`, appelé par `consulter` et `annuler` avant toute autre décision : si le demandeur n'est pas bibliothécaire et que `reservation.adherent.userId` n'est pas le sien, `ForbiddenException` → 403 via `ReservationExceptionHandler.accesRefuse`. |
| **RS-04** — l'identité vient du token | `ReservationService.determinerAdherent`, premier contrôle de `creer` : pour un ADHERENT, l'adhérent de la réservation est `demandeur.getUserId()`, lu dans l'`UtilisateurAuthentifie` que `JwtService.loadUserByUsername` a construit depuis le sujet du jeton ; l'`adherentId` du corps est ignoré s'il est absent ou égal, refusé en 403 s'il désigne quelqu'un d'autre. Le contrôleur reçoit ce principal par `@AuthenticationPrincipal` et ne lit jamais l'identité ailleurs. |
| **RS-05** — un ADHERENT ne liste que les siennes | `ReservationService.lister` : pour un ADHERENT, le filtre `adherentId` est forcé à `demandeur.getUserId()` (donc `findByAdherent_UserId`, jamais `findAll`) ; un `adherentId` explicite qui n'est pas le sien vaut 403. |

### 401 ou 403

Deux composants distincts, que Spring Security choisit selon que la requête
porte ou non une authentification :

- `JwtAuthenticationEntryPoint` — **401**, *je ne sais pas qui vous êtes* : appelé
  uniquement pour l'anonyme. Un DELETE sans jeton donne bien 401, pas 403 : on ne
  peut pas manquer d'un rôle sans être quelqu'un.
- `AccesInterditHandler` et les deux handlers 403 de `ReservationExceptionHandler`
  — **403**, *je sais qui vous êtes mais vous n'avez pas le droit* : jamais
  atteints par un anonyme, la chaîne de filtres l'a arrêté avant.

Les deux répondent avec le même corps `ApiError` que le reste du module :

```json
{ "horodatage": "2026-09-12T21:40:00.000", "statut": 403, "erreur": "Forbidden",
  "message": "RS-04 : un adhérent ne réserve qu'en son nom ; adherentId=3 n'est pas le vôtre (2)." }
```

---

## Les tests

### Unitaire — RG-03, repository simulé

`ReservationServiceTest` (Mockito, aucun contexte Spring, aucune base) :

- `rg03_accepte_la_troisieme_reservation_active` — le dépôt compte 2 actives,
  la création passe et `save` est appelé ;
- `rg03_refuse_la_quatrieme_reservation_active` — le dépôt compte 3 actives,
  `ConflictException` dont le message commence par `RG-03`, `save` jamais appelé.

La même classe couvre RS-03, RS-04 et RS-05 au niveau du service (huit tests),
et RG-01 sur un livre disponible puis indisponible (bonus).

### Intégration — GET /api/reservations sécurisé

`ReservationControllerSecuriteTest` : `@SpringBootTest` + `MockMvc`, tout est
réel — la chaîne de filtres Spring Security, les jetons obtenus par un vrai
`POST /authenticate` (mot de passe BCrypt), le contrôleur, le service, le
handler d'erreurs et la base PostgreSQL du projet. Chaque test crée ses trois
comptes (`it-a1`, `it-a2`, `it-admin`) et deux réservations dans une
transaction annulée à la fin : la base ressort intacte.

- `lister_sans_token_repond_401`
- `lister_avec_token_adherent_repond_200_avec_ses_reservations`
- `consulter_la_reservation_d_un_autre_repond_403`

plus, dans la même classe : 401 sur chaque route, 401 « jeton expiré », 401 sur
un jeton illisible (et non 500), 403 sur le DELETE d'un adhérent, 204 pour le
bibliothécaire, 403 sur RS-04 et 201 au nom du porteur du jeton sans
`adherentId` dans le corps.

`BibliothequeApplicationTests.contextLoads` rendait `mvn test` rouge dès qu'on
le lançait hors de `docker compose` : sans les variables d'environnement qui
pointent vers PostgreSQL, il lisait le `application.properties` figé (URL MySQL,
pilote absent). Le profil `test` joue désormais le rôle de `docker-compose.yml`
pour les tests : URL PostgreSQL, dialecte PostgreSQL, sans toucher à
`application.properties` ni à `pom.xml`.

Au passage, un bug de jeu de données révélé par ces tests : `seed.sql` et la
fixture insèrent les rôles avec `role_id` 1 et 2 explicites sans recaler la
séquence `role_role_id_seq` — le prochain rôle créé par l'application aurait
violé la clé primaire. Les deux scripts font maintenant le `setval`, comme
pour `hibernate_sequence`.

---

## Bonus

- **Expiration du jeton** : `JwtRequestFilter` intercepte `ExpiredJwtException`
  et dépose le motif dans la requête ; le 401 dit alors
  `Le jeton a expiré : reconnectez-vous via POST /authenticate.` Un jeton
  malformé ou mal signé, qui remontait jusqu'à Tomcat en 500, donne aussi 401.
- **RG-01** : `rg01_accepte_un_livre_indisponible` complète
  `rg01_refuse_un_livre_disponible`.
- **Journalisation des refus** : chaque 401 et 403 laisse une ligne `WARN`
  `[securite] …` avec la méthode, l'URI, l'identité du demandeur et la règle
  enfreinte — dans `JwtAuthenticationEntryPoint`, `AccesInterditHandler` et
  `ReservationExceptionHandler.journaliserRefus`.

---

## Rejouer la démonstration

`docker/fixture-reservation.sql` prépare les trois comptes de l'énoncé, chaque
adhérent ayant une réservation à son nom :

| Compte | Mot de passe | Rôle | userId | Réservation |
|---|---|---|---|---|
| `admin` | `admin123` | BIBLIOTHECAIRE | 1 | — |
| `a1` | `admin123` | ADHERENT | 2 | **100** sur L2 |
| `a2` | `admin123` | ADHERENT | 3 | **101** sur L3 |

```bash
docker compose up -d
docker cp docker/fixture-reservation.sql bibliotheque-db:/tmp/fixture.sql
MSYS_NO_PATHCONV=1 docker exec bibliotheque-db sh -c \
  'PGCLIENTENCODING=UTF8 psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/fixture.sql'
```

Les refus à provoquer, avec le jeton de `a1` :

| Requête | Attendu |
|---|---|
| `GET /api/reservations` sans en-tête | 401 |
| `GET /api/reservations` | 200, uniquement la 100 |
| `GET /api/reservations?adherentId=3` | 403 RS-05 |
| `GET /api/reservations/101` | 403 RS-03 |
| `PATCH /api/reservations/101/annuler` | 403 RS-03 |
| `POST /api/reservations` `{ "livreId": 4, "adherentId": 3 }` | 403 RS-04 |
| `POST /api/reservations` `{ "livreId": 4 }` | 201, `adherentId: 2` |
| `DELETE /api/reservations/100` | 403 RS-02 |

Les mêmes avec le jeton d'`admin` passent toutes (200, 201, 204).

La documentation Swagger (<http://localhost:8081>) décrit les 401 et 403 de
chaque route et le nouveau statut d'`adherentId` selon le rôle.
