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
	void appliesAllFlywayMigrations() {
		var info = flyway.info();

		assertNotNull(info.current(), "Flyway should apply at least the initial database migration");
		assertEquals(0, info.pending().length, "All Flyway migrations should be applied without pending scripts");
	}

}
