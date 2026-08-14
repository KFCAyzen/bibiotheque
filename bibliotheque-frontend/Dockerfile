# ==============================================================================
# Étage 1 — construction Angular
# ------------------------------------------------------------------------------
# Node 16 et non 20 ou 22 : le projet est en Angular 14, dont le support
# officiel s'arrête à Node 16. Sur Node 22, "ng version" affiche
# "Node: 22.x (Unsupported)". Le build passe malgré tout, mais on ne construit
# pas une image de livraison sur une combinaison hors support.
# package.json est intouchable (contrainte de l'épreuve) : on aligne donc
# l'environnement sur le projet, exactement comme pour le JDK 8 du backend.
# ==============================================================================
FROM node:16-alpine AS build

WORKDIR /build

# package.json et package-lock.json d'abord : tant qu'ils ne changent pas,
# Docker réutilise la couche et évite de réinstaller 900 paquets.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

RUN npm run build


# ==============================================================================
# Étage 2 — service des fichiers statiques
# ------------------------------------------------------------------------------
# Un build Angular n'est que du HTML, du CSS et du JavaScript : il n'a besoin
# ni de Node ni du CLI pour être servi. L'image finale ne contient donc que
# nginx et les fichiers compilés.
#
# Rien à configurer ici pour joindre l'API : le code appelle
# http://localhost:8080 en dur, mais ce code s'exécute dans le NAVIGATEUR.
# "localhost" y désigne la machine de l'utilisateur, où le port 8080 du service
# backend est justement publié par docker-compose.yml. La requête aboutit donc
# sans proxy — et le CorsConfiguration du backend autorise toutes les origines.
# ==============================================================================
FROM nginx:alpine

COPY --from=build /build/dist/bibliotheque-frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 4200
