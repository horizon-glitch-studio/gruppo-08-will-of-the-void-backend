package com.horizonglitch.wotv.config;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The random source of pack draws: unpredictable, so the next card cannot be guessed. */
@Configuration
public class RandomConfig {

	@Bean
	public RandomGenerator randomGenerator() {
		return new SecureRandom();
	}
}
