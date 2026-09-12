package com.ibizabroker.bibliotheque.configuration;

import com.ibizabroker.bibliotheque.service.JwtService;
import com.ibizabroker.bibliotheque.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Lit l'en-tête Authorization et, si le token est valable, installe
 * l'utilisateur dans le contexte de sécurité.
 *
 * Ce filtre ne refuse jamais rien lui-même. Un token absent, expiré ou
 * illisible laisse simplement la requête anonyme ; c'est ensuite la
 * configuration de sécurité qui constate l'absence d'authentification et
 * délègue à JwtAuthenticationEntryPoint le soin de répondre 401 (RS-01).
 * Il dépose seulement, dans l'attribut ATTRIBUT_MOTIF, la raison du refus,
 * pour que le 401 puisse dire au client s'il doit se reconnecter (token
 * expiré) ou corriger sa requête (token illisible).
 */
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtRequestFilter.class);

    /** Attribut de requête lu par JwtAuthenticationEntryPoint. */
    public static final String ATTRIBUT_MOTIF = JwtRequestFilter.class.getName() + ".motif";

    public static final String MOTIF_ABSENT = "absent";
    public static final String MOTIF_EXPIRE = "expire";
    public static final String MOTIF_INVALIDE = "invalide";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        final String requestTokenHeader = request.getHeader("Authorization");

        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            request.setAttribute(ATTRIBUT_MOTIF, MOTIF_ABSENT);
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = requestTokenHeader.substring(7);

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String username = jwtUtil.getUsernameFromToken(jwtToken);
                UserDetails userDetails = jwtService.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    request.setAttribute(ATTRIBUT_MOTIF, MOTIF_INVALIDE);
                }
            } catch (ExpiredJwtException e) {
                // Le bonus de l'énoncé : un token expiré est signalé comme tel,
                // le client sait qu'il doit se reconnecter plutôt que chercher
                // une erreur dans sa requête.
                request.setAttribute(ATTRIBUT_MOTIF, MOTIF_EXPIRE);
                LOGGER.info("[securite] token expire pour {} sur {} {}",
                        e.getClaims().getSubject(), request.getMethod(), request.getRequestURI());
            } catch (JwtException | IllegalArgumentException e) {
                // Signature fausse, format illisible, token vide : sans ce
                // filet, jjwt remontait jusqu'à Tomcat et la réponse était un
                // 500 — un client anonyme ne doit jamais voir autre chose que 401.
                request.setAttribute(ATTRIBUT_MOTIF, MOTIF_INVALIDE);
                LOGGER.warn("[securite] token illisible sur {} {} : {}",
                        request.getMethod(), request.getRequestURI(), e.getMessage());
            } catch (UsernameNotFoundException e) {
                // Token signé pour un compte qui n'existe plus.
                request.setAttribute(ATTRIBUT_MOTIF, MOTIF_INVALIDE);
                LOGGER.warn("[securite] token pour un compte inconnu sur {} {}",
                        request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

}
