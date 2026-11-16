package com.horizonglitch.wotv;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Hibernate validates every entity against the tables created by Flyway: the context does not start if a
 * table, a column or a column type of the entities does not match V1__init.sql.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SchemaValidationTest {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void entitiesMatchTheFlywaySchema() {
		assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(31);
	}
}
