-- =============================================================================
-- Jeu de données initial — exécuté par le service "seed" de docker-compose.yml
-- -----------------------------------------------------------------------------
-- POURQUOI CE FICHIER N'EST PAS DANS docker-entrypoint-initdb.d
--
-- Le point d'entrée de l'image PostgreSQL n'exécute ses scripts qu'à la toute
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
-- sur ON CONFLICT DO NOTHING avec des clés primaires explicites : un doublon est
-- ignoré au lieu de faire échouer le script — d'autant que psql tourne ici avec
-- -v ON_ERROR_STOP=1, où la moindre erreur avorterait tout le seed.
--
-- C'est la traduction directe de l'INSERT IGNORE de MySQL, qui n'existe pas en
-- PostgreSQL. La clause « ON CONFLICT » sans cible couvre n'importe quelle
-- contrainte d'unicité de la table, donc la clé primaire posée ci-dessous.
--
-- Les noms de colonnes ne sont pas supposés : ils viennent du \d des tables
-- réellement créées par Hibernate sous le dialecte PostgreSQL95Dialect.
-- =============================================================================

-- ENCODAGE — à ne surtout pas omettre.
--
-- Ce fichier est écrit en UTF-8, mais le client ne le devine pas : il annonce au
-- serveur le jeu de caractères de son environnement, qui ré-encode alors chaque
-- octet reçu. « L'Étranger » finit stocké « L'Ã‰tranger » : un double encodage,
-- invisible tant qu'on ne regarde pas les octets.
--
-- Le service seed pose déjà PGCLIENTENCODING=UTF8 ; cette ligne verrouille le
-- réglage côté script, pour le cas où le fichier serait rejoué à la main.
SET client_encoding = 'UTF8';

-- Les rôles. role_id est alimenté par la séquence role_role_id_seq, mais on le
-- fixe explicitement pour rendre l'insertion rejouable et les liaisons
-- ci-dessous déterministes.
INSERT INTO role (role_id, role_name) VALUES
    (1, 'Admin'),
    (2, 'User')
ON CONFLICT DO NOTHING;

-- Les comptes. Le mot de passe ne peut pas être en clair : WebSecurityConfiguration
-- déclare un BCryptPasswordEncoder. Le haché ci-dessous correspond à "admin123"
-- pour les deux comptes.
INSERT INTO users (user_id, username, name, password) VALUES
    (1, 'admin', 'Administrateur', '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696'),
    (2, 'marie', 'Marie Dupont',   '$2b$10$RN5ij7XXjDpRBALhITW.2uzYGontX4U9c9ZRH5i3e.5l6RvkjZ696')
ON CONFLICT DO NOTHING;

-- Qui a quel rôle. Deux profils pour pouvoir démontrer la page "forbidden" :
-- admin -> Admin, marie -> User.
INSERT INTO user_role (user_id, role_id) VALUES
    (1, 1),
    (2, 2)
ON CONFLICT DO NOTHING;

-- Quelques livres, pour que la liste ne soit pas vide au premier écran.
INSERT INTO books (book_id, book_name, book_author, book_genre, no_of_copies) VALUES
    (1, 'Le Petit Prince',         'Antoine de Saint-Exupéry', 'Conte',       5),
    (2, 'L''Étranger',              'Albert Camus',             'Roman',       3),
    (3, 'Une si longue lettre',    'Mariama Bâ',               'Roman',       4),
    (4, 'Ville cruelle',           'Mongo Beti',               'Roman',       2),
    (5, 'Le Vieux Nègre et la Médaille', 'Ferdinand Oyono',    'Roman',       3)
ON CONFLICT DO NOTHING;

-- ÉTAPE INDISPENSABLE, et facile à oublier.
--
-- books et users déclarent GenerationType.AUTO : leur identifiant vient de
-- hibernate_sequence, pas de la colonne. Le compteur vaut 1 après la création du
-- schéma : sans ce recalage, la première création depuis l'application viserait
-- book_id = 1, déjà pris ci-dessus, et échouerait sur une violation de clé.
--
-- Sous MySQL, hibernate_sequence était une TABLE à une colonne next_val, qu'on
-- corrigeait par un UPDATE. PostgreSQL sait faire des séquences : Hibernate en
-- crée une vraie, et c'est setval() qui la déplace.
--
-- Le troisième argument (is_called = false) dit « le prochain nextval() rendra
-- exactement 100 » ; à true, il rendrait 101.
--
-- Le WHERE reprend le « WHERE next_val < 100 » d'origine : on ne fait jamais
-- redescendre un compteur déjà plus haut, sans quoi rejouer ce seed après
-- quelques créations réintroduirait des identifiants déjà distribués.
-- COALESCE couvre le cas d'une séquence dont pg_sequences.last_value vaut NULL,
-- ce qui arrive tant que nextval() n'a jamais été appelé — donc juste après la
-- création du schéma, mais aussi après un setval(..., false). Sans lui, la
-- comparaison NULL < 100 ne vaudrait pas « vrai » mais « inconnu », et le
-- recalage serait silencieusement sauté au moment précis où il est nécessaire.
--
-- Conséquence assumée : rejouer ce seed sur une base intacte refait le setval,
-- ce qui ne change rien. Dès que l'application a distribué un identifiant, en
-- revanche, last_value porte une vraie valeur et le compteur est laissé en paix.
SELECT setval('hibernate_sequence', 100, false)
WHERE COALESCE(
        (SELECT last_value FROM pg_sequences
          WHERE schemaname = 'public' AND sequencename = 'hibernate_sequence'),
        0) < 100;
