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
 * L'application tourne sur PostgreSQL, mais seulement parce que
 * docker-compose.yml injecte SPRING_DATASOURCE_URL et le dialecte par
 * variables d'environnement. « mvn test » lancé seul n'a pas ces variables :
 * ce test lisait alors le application.properties figé de la séance 1 (URL
 * MySQL, pilote absent du classpath) et échouait avant d'exécuter le moindre
 * test utile. Le projet n'embarque aucune base en mémoire et pom.xml est
 * intouchable ; l'énoncé exige des tests qui passent sans base qui tourne.
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
