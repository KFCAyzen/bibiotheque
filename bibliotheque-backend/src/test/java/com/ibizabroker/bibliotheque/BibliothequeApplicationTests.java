package com.ibizabroker.bibliotheque;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Le contexte Spring démarre-t-il, base comprise ?
 *
 * Le profil « test » (src/test/resources/application-test.properties) vise le
 * conteneur PostgreSQL du projet : Hibernate valide le schéma contre la vraie
 * base, avec le vrai dialecte. Prérequis : docker compose up -d db.
 */
@SpringBootTest
@ActiveProfiles("test")
class BibliothequeApplicationTests {

	@Test
	void contextLoads() {
	}

}
