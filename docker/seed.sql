-- =============================================================================
-- Jeu de données initial — exécuté par le service "seed" de docker-compose.yml
-- -----------------------------------------------------------------------------
-- POURQUOI CE FICHIER N'EST PAS DANS docker-entrypoint-initdb.d
--
-- Le point d'entrée de l'image MySQL n'exécute ses scripts qu'à la toute
-- première initialisation du volume, donc AVANT que le backend n'ait démarré.
-- Or les tables n'existent pas à ce moment-là : c'est Hibernate qui les crée au
-- premier démarrage de l'API (spring.jpa.hibernate.ddl-auto=update).
-- Un script placé là échouerait sur des tables inexistantes.
--
-- D'où un service dédié, déclenché seulement quand le healthcheck du backend
-- passe au vert — c'est-à-dire une fois Tomcat démarré, donc une fois le schéma
-- créé.
--
-- IDEMPOTENCE
--
-- Le service rejoue ce fichier à chaque "docker compose up". On s'appuie donc
-- sur INSERT IGNORE avec des clés primaires explicites : un doublon est ignoré
-- au lieu de faire échouer le script. Les noms de colonnes ci-dessous ne sont
-- pas supposés, ils viennent du DESCRIBE des tables réellement créées.
-- =============================================================================

-- ENCODAGE — à ne surtout pas omettre.
--
-- Ce fichier est écrit en UTF-8, mais le client mysql ne le devine pas : il
-- annonce au serveur le jeu de caractères par défaut de son environnement
-- (latin1), qui ré-encode alors chaque octet reçu. « L'Étranger » finit stocké
-- « L'Ã‰tranger » : un double encodage, invisible tant qu'on ne regarde pas
-- les octets avec HEX().
--
-- SET NAMES aligne les trois réglages de la connexion (client, résultats,
-- connexion) sur utf8mb4. Le service seed passe en plus
-- --default-character-set=utf8mb4 en ligne de commande : la ceinture et les
-- bretelles, car ce SET NAMES ne protège que ce qui le suit.
SET NAMES utf8mb4;

-- Les rôles. role_id est auto_increment, mais on le fixe explicitement pour
-- rendre l'insertion rejouable et les liaisons ci-dessous déterministes.
INSERT IGNORE INTO role (role_id, role_name) VALUES
    (1, 'Admin'),
    (2, 'User');

-- Les comptes. Le mot de passe ne peut pas être en clair : WebSecurityConfiguration
-- déclare un BCryptPasswordEncoder. Le haché ci-dessous correspond à "admin123"
-- pour les deux comptes.
INSERT IGNORE INTO users (user_id, username, name, password) VALUES
    (1, 'admin', 'Administrateur', '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696'),
    (2, 'marie', 'Marie Dupont',   '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696');

-- Qui a quel rôle. Deux profils pour pouvoir démontrer la page "forbidden" :
-- admin -> Admin, marie -> User.
INSERT IGNORE INTO user_role (user_id, role_id) VALUES
    (1, 1),
    (2, 2);

-- Quelques livres, pour que la liste ne soit pas vide au premier écran.
INSERT IGNORE INTO books (book_id, book_name, book_author, book_genre, no_of_copies) VALUES
    (1, 'Le Petit Prince',         'Antoine de Saint-Exupéry', 'Conte',       5),
    (2, 'L''Étranger',              'Albert Camus',             'Roman',       3),
    (3, 'Une si longue lettre',    'Mariama Bâ',               'Roman',       4),
    (4, 'Ville cruelle',           'Mongo Beti',               'Roman',       2),
    (5, 'Le Vieux Nègre et la Médaille', 'Ferdinand Oyono',    'Roman',       3);

-- ÉTAPE INDISPENSABLE, et facile à oublier.
-- books et users tirent leur identifiant de hibernate_sequence, pas d'un
-- auto_increment. Le compteur vaut 1 après la création du schéma : sans ce
-- UPDATE, la première création depuis l'application viserait book_id = 1,
-- déjà pris ci-dessus, et échouerait sur une violation de clé primaire.
UPDATE hibernate_sequence SET next_val = 100 WHERE next_val < 100;
