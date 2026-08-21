-- =============================================================================
-- Jeu de données de test du module Réservation
-- -----------------------------------------------------------------------------
--   L1              un livre DISPONIBLE, aucun emprunt en cours
--   L2, L3, L4, L5  quatre livres TOUS EMPRUNTÉS ET NON RENDUS
--   A1              l'adhérent réservataire principal
--   A2              l'adhérent qui saturera son quota (RG-03)
--   A3              l'emprunteur : c'est lui qui détient L2 à L5
--
-- Le compte « admin » est conservé : sans lui, plus de POST /authenticate,
-- donc plus de jeton, donc plus aucune route utilisable depuis Swagger.
--
-- Les identifiants réutilisent ceux de docker/seed.sql (livres 1 à 5,
-- utilisateurs 1 à 4) pour que le service « seed », qui rejoue ses INSERT
-- IGNORE à chaque « docker compose up », les saute au lieu de réintroduire
-- l'ancien contenu par-dessus celui-ci.
-- =============================================================================

SET NAMES utf8mb4;

-- --- Table rase, dans l'ordre imposé par les clés étrangères -----------------
DELETE FROM reservation;
DELETE FROM borrow;
DELETE FROM user_role;
DELETE FROM users;
DELETE FROM books;

-- --- Les compteurs des tables à AUTO_INCREMENT -------------------------------
-- Reservation et Borrow déclarent GenerationType.IDENTITY : leur clé vient de
-- l'AUTO_INCREMENT de MySQL, un compteur distinct de hibernate_sequence — que
-- vider la table ne remet pas à zéro, et que le UPDATE du bas ne touche pas.
--
-- On les démarre à 100 pour que ces identifiants ne puissent jamais être
-- confondus avec un livreId (1 à 5) ou un adherentId (1 à 4) pendant les tests.
ALTER TABLE reservation AUTO_INCREMENT = 100;
ALTER TABLE borrow AUTO_INCREMENT = 100;

-- --- Les rôles ---------------------------------------------------------------
INSERT IGNORE INTO role (role_id, role_name) VALUES
    (1, 'Admin'),
    (2, 'User');

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
INSERT INTO borrow (book_id, user_id, issue_date, due_date, return_date) VALUES
    (2, 4, NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 5 DAY, NULL),
    (3, 4, NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 5 DAY, NULL),
    (4, 4, NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 5 DAY, NULL),
    (5, 4, NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 5 DAY, NULL);

-- --- Le compteur d'identifiants ----------------------------------------------
-- books et users tirent leur clé de hibernate_sequence, pas d'un auto_increment.
-- Le laisser sous le plus grand identifiant posé ci-dessus ferait échouer la
-- première création depuis l'application sur une violation de clé primaire.
UPDATE hibernate_sequence SET next_val = 100 WHERE next_val < 100;
