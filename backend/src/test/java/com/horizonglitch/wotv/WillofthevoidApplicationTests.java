package com.horizonglitch.wotv;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class WillofthevoidApplicationTests {

	@Autowired
	private Flyway flyway;

	@Test
	void contextLoads() {
	}

	@Test
	void appliesInitialFlywayMigration() {
		var current = flyway.info().current();

		assertNotNull(current, "Flyway should apply the initial database migration");
		assertEquals("1", current.getVersion().getVersion());
	}

}
