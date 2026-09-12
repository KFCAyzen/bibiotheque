package com.ibizabroker.bibliotheque.security;

import com.ibizabroker.bibliotheque.entity.Role;

import java.util.Arrays;
import java.util.Optional;

/**
 * Les deux rôles métier de l'énoncé, ADHERENT et BIBLIOTHECAIRE, adossés au
 * système de rôles déjà en base.
 *
 * Le projet possède depuis la séance 1 une table ROLE à deux lignes, « User »
 * et « Admin », que le front Angular lit dans la réponse d'authentification
 * pour choisir sa page d'accueil. Renommer ces lignes aurait cassé ce contrat.
 * On les conserve donc telles quelles et on leur donne ici leur sens métier :
 * un « User » est un adhérent, un « Admin » est un bibliothécaire.
 *
 * C'est le seul endroit où cette correspondance est écrite. JwtService s'en
 * sert pour attribuer, à chaque connexion, l'autorité ROLE_ADHERENT ou
 * ROLE_BIBLIOTHECAIRE, que les @PreAuthorize du module Réservation consultent.
 */
public enum RoleMetier {

    ADHERENT("User"),

    BIBLIOTHECAIRE("Admin");

    /** Le libellé stocké en base, colonne role.role_name. */
    private final String roleNameEnBase;

    RoleMetier(String roleNameEnBase) {
        this.roleNameEnBase = roleNameEnBase;
    }

    /** Le nom d'autorité Spring Security : hasRole('ADHERENT') attend ROLE_ADHERENT. */
    public String autorite() {
        return "ROLE_" + name();
    }

    /** Traduit une ligne de la table ROLE ; vide si le libellé n'est pas connu. */
    public static Optional<RoleMetier> depuis(Role role) {
        if (role == null || role.getRoleName() == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(candidat -> candidat.roleNameEnBase.equalsIgnoreCase(role.getRoleName().trim()))
                .findFirst();
    }
}
