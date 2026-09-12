package com.ibizabroker.bibliotheque.security;

import com.ibizabroker.bibliotheque.entity.Users;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * L'utilisateur tel que le module Réservation le connaît : celui que le token
 * désigne, et lui seul.
 *
 * Avant cette séance, JwtService renvoyait un
 * org.springframework.security.core.userdetails.User, qui ne porte que le nom
 * de connexion. Or les règles RS-03 à RS-05 comparent des identifiants : il
 * faut que le userId voyage avec l'authentification, sinon le service devrait
 * refaire une requête en base à chaque appel — ou pire, faire confiance à
 * l'adherentId du corps de la requête, ce que RS-04 interdit précisément.
 *
 * C'est cet objet que le contrôleur reçoit via @AuthenticationPrincipal et
 * transmet au service. Il est immuable et ne contient rien que le token ne
 * garantisse pas.
 */
public class UtilisateurAuthentifie implements UserDetails {

    private final Integer userId;
    private final String username;
    private final String password;
    private final String nom;
    private final Set<RoleMetier> rolesMetier;
    private final Set<GrantedAuthority> autorites;

    public UtilisateurAuthentifie(Integer userId, String username, String password, String nom,
                                  Set<RoleMetier> rolesMetier, Set<GrantedAuthority> autorites) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nom = nom;
        this.rolesMetier = rolesMetier.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(rolesMetier));
        this.autorites = Collections.unmodifiableSet(new LinkedHashSet<>(autorites));
    }

    /**
     * Construit le principal à partir de la ligne users et de ses rôles.
     *
     * Chaque rôle en base donne deux autorités : l'historique
     * « ROLE_Admin » / « ROLE_User », que les contrôleurs de la séance 1
     * consultent toujours, et son équivalent métier « ROLE_BIBLIOTHECAIRE » /
     * « ROLE_ADHERENT ». Les anciennes routes ne changent donc pas de
     * comportement, et les nouvelles parlent le vocabulaire de l'énoncé.
     */
    public static UtilisateurAuthentifie depuis(Users user) {
        Set<RoleMetier> rolesMetier = EnumSet.noneOf(RoleMetier.class);
        Set<GrantedAuthority> autorites = new LinkedHashSet<>();

        if (user.getRole() != null) {
            user.getRole().forEach(role -> {
                autorites.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName()));
                RoleMetier.depuis(role).ifPresent(roleMetier -> {
                    rolesMetier.add(roleMetier);
                    autorites.add(new SimpleGrantedAuthority(roleMetier.autorite()));
                });
            });
        }

        return new UtilisateurAuthentifie(
                user.getUserId(), user.getUsername(), user.getPassword(), user.getName(),
                rolesMetier, autorites);
    }

    public Integer getUserId() {
        return userId;
    }

    public String getNom() {
        return nom;
    }

    public boolean aLeRole(RoleMetier role) {
        return rolesMetier.contains(role);
    }

    public boolean estBibliothecaire() {
        return aLeRole(RoleMetier.BIBLIOTHECAIRE);
    }

    // --- UserDetails ---------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return autorites;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return username + " (userId=" + userId + ", roles=" + rolesMetier + ")";
    }
}
