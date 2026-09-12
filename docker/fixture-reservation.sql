-- =============================================================================
-- Jeu de données de test du module Réservation
-- -----------------------------------------------------------------------------
--   L1              un livre DISPONIBLE, aucun emprunt en cours
--   L2, L3, L4, L5  quatre livres TOUS EMPRUNTÉS ET NON RENDUS
--   A1              l'adhérent réservataire principal
--   A2              l'adhérent qui saturera son quota (RG-03)
--   A3              l'emprunteur : c'est lui qui détient L2 à L5
--   admin           le BIBLIOTHECAIRE (rôle Admin)
--
-- Séance 4 : A1 et A2 sont les deux comptes ADHERENT (rôle User) de la
-- démonstration de sécurité, chacun avec une réservation à son nom —
-- la 100 pour A1, la 101 pour A2. C'est ce qui permet de provoquer les
-- refus RS-03 (A1 sur la 101) et RS-04 (A1 réservant pour A2) en direct.
--
-- Le compte « admin » est conservé : sans lui, plus de POST /authenticate,
-- donc plus de jeton, donc plus aucune route utilisable depuis Swagger.
--
-- Les identifiants réutilisent ceux de docker/seed.sql (livres 1 à 5,
-- utilisateurs 1 à 4) pour que le service « seed », qui rejoue ses insertions
-- « ON CONFLICT DO NOTHING » à chaque « docker compose up », les saute au lieu
-- de réintroduire l'ancien contenu par-dessus celui-ci.
-- =============================================================================

SET client_encoding = 'UTF8';

-- --- Table rase, dans l'ordre imposé par les clés étrangères -----------------
DELETE FROM reservation;
DELETE FROM borrow;
DELETE FROM user_role;
DELETE FROM users;
DELETE FROM books;

-- --- Les compteurs des colonnes auto-incrémentées ----------------------------
-- Reservation et Borrow déclarent GenerationType.IDENTITY : sous PostgreSQL,
-- Hibernate leur donne une colonne serial, donc une séquence dédiée nommée
-- <table>_<colonne>_seq — distincte de hibernate_sequence, que vider la table
-- ne remet pas à zéro et que le setval du bas ne touche pas.
--
-- ALTER SEQUENCE ... RESTART WITH est l'équivalent direct de l'ancien
-- ALTER TABLE ... AUTO_INCREMENT = de MySQL.
--
-- On les démarre à 100 pour que ces identifiants ne puissent jamais être
-- confondus avec un livreId (1 à 5) ou un adherentId (1 à 4) pendant les tests.
-- Les deux réservations posées plus bas prennent 100 et 101 : la séquence
-- repart donc à 102 pour celles que l'application créera.
ALTER SEQUENCE reservation_reservation_id_seq RESTART WITH 102;
ALTER SEQUENCE borrow_borrow_id_seq RESTART WITH 100;

-- --- Les rôles ---------------------------------------------------------------
INSERT INTO role (role_id, role_name) VALUES
    (1, 'Admin'),
    (2, 'User')
ON CONFLICT DO NOTHING;

-- Role déclare GenerationType.IDENTITY : sa colonne serial a sa propre
-- séquence, que les identifiants explicites ci-dessus ne font pas avancer.
-- Sans ce recalage, le prochain rôle créé par l'application viserait
-- role_id = 1, déjà pris. Même piège que hibernate_sequence plus bas.
SELECT setval('role_role_id_seq', (SELECT MAX(role_id) FROM role));

-- --- Les livres --------------------------------------------------------------
-- no_of_copies est le seul indicateur de disponibilité que lise RG-01 :
-- > 0 le livre est en rayon, = 0 il est réservable.
INSERT INTO books (book_id, book_name, book_author, book_genre, no_of_copies) VALUES
    (1, 'L1 — Le Petit Prince',              'Antoine de Saint-Exupéry', 'Conte', 3),
    (2, 'L2 — L''Étranger',                  'Albert Camus',             'Roman', 0),
    (3, 'L3 — Une si longue lettre',         'Mariama Bâ',               'Roman', 0),
    (4, 'L4 — Ville cruelle',                'Mongo Beti',               'Roman', 0),
    (5, 'L5 — Le Vieux Nègre et la Médaille','Ferdinand Oyono',          'Roman', 0);

-- --- Les comptes -------------------------------------------------------------
-- Mot de passe « admin123 » pour tous : le haché BCrypt est celui de seed.sql.
INSERT INTO users (user_id, username, name, password) VALUES
    (1, 'admin', 'Administrateur',              '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696'),
    (2, 'a1',    'A1 Réservataire principal',   '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696'),
    (3, 'a2',    'A2 Quota à saturer',          '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696'),
    (4, 'a3',    'A3 Emprunteur',               '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696');

INSERT INTO user_role (user_id, role_id) VALUES
    (1, 1),
    (2, 2),
    (3, 2),
    (4, 2);

-- --- Les emprunts en cours ---------------------------------------------------
-- A3 détient L2 à L5. return_date reste NULL : « empruntés et non rendus ».
-- C'est ce qui justifie no_of_copies = 0 sur ces quatre livres.
--
-- La syntaxe d'intervalle change de moteur : MySQL écrivait INTERVAL 2 DAY,
-- PostgreSQL attend un littéral, INTERVAL '2 days'.
INSERT INTO borrow (book_id, user_id, issue_date, due_date, return_date) VALUES
    (2, 4, NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', NULL),
    (3, 4, NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', NULL),
    (4, 4, NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', NULL),
    (5, 4, NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', NULL);

-- --- Les réservations de départ (séance 4) -----------------------------------
-- Une par adhérent, sur des livres empruntés (RG-01), au statut EN_ATTENTE,
-- valables encore six jours (RG-04 : sept jours depuis la veille).
--   100 : A1 sur L2      101 : A2 sur L3
-- A3 n'en a pas : c'est l'emprunteur, il détient déjà les livres.
INSERT INTO reservation (reservation_id, book_id, user_id, date_reservation, date_expiration, statut) VALUES
    (100, 2, 2, NOW() - INTERVAL '1 day', NOW() + INTERVAL '6 days', 'EN_ATTENTE'),
    (101, 3, 3, NOW() - INTERVAL '1 day', NOW() + INTERVAL '6 days', 'EN_ATTENTE');

-- --- Le compteur d'identifiants ----------------------------------------------
-- books et users tirent leur clé de hibernate_sequence, une vraie SEQUENCE sous
-- PostgreSQL là où MySQL n'offrait qu'une table à colonne next_val. Le laisser
-- sous le plus grand identifiant posé ci-dessus ferait échouer la première
-- création depuis l'application sur une violation de clé primaire.
--
-- is_called = false : le prochain nextval() rendra exactement 100.
SELECT setval('hibernate_sequence', 100, false)
WHERE COALESCE(
        (SELECT last_value FROM pg_sequences
          WHERE schemaname = 'public' AND sequencename = 'hibernate_sequence'),
        0) < 100;
