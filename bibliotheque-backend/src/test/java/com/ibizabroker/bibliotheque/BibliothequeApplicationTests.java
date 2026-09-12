package com.ibizabroker.bibliotheque;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.BorrowRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Le contexte Spring démarre-t-il ?
 *
 * Le projet n'embarque aucune base en mémoire et pom.xml est intouchable :
 * sans les exclusions ci-dessous, ce test tentait d'ouvrir une connexion
 * MySQL sur localhost et échouait sur toute machine sans base — donc
 * « mvn test » était rouge avant même d'exécuter le moindre test utile.
 *
 * On écarte donc l'auto-configuration JPA et on remplace les dépôts par des
 * doublures : tout le reste — sécurité, contrôleurs, services, planificateur
 * — est câblé pour de vrai, ce qui est précisément ce qu'on veut vérifier.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class BibliothequeApplicationTests {

	@MockBean
	private BooksRepository booksRepository;

	@MockBean
	private UsersRepository usersRepository;

	@MockBean
	private BorrowRepository borrowRepository;

	@MockBean
	private ReservationRepository reservationRepository;

	@Test
	void contextLoads() {
	}

}
