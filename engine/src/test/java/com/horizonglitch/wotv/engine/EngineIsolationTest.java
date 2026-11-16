package com.horizonglitch.wotv.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The engine module must not see the frameworks used by the API module. */
class EngineIsolationTest {

	@Test
	void frameworksAreNotOnTheEngineClasspath() {
		List<String> frameworkClasses = List.of(
				"org.springframework.boot.SpringApplication",
				"jakarta.persistence.Entity",
				"org.hibernate.Session",
				"jakarta.servlet.Servlet");

		for (String name : frameworkClasses) {
			assertThat(isPresent(name)).as("%s should not be on the engine classpath", name).isFalse();
		}
	}

	private static boolean isPresent(String className) {
		try {
			Class.forName(className, false, EngineIsolationTest.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
