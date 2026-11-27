package com.horizonglitch.wotv.season;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RANK-08 with SEASON_RESET_THRESHOLD = 1000, rounded to the nearest whole number with a half rounded up. */
class SeasonResetTest {

	private static final int THRESHOLD = 1000;

	@Test
	void orbitNamesAndTrophyRangesComeFromTheRule() {
		assertThat(Orbit.ORBIT_1.englishName()).isEqualTo("Brink Orbit");
		assertThat(Orbit.ORBIT_1.italianName()).isEqualTo("Orbita del Bordo");
		assertThat(Orbit.fromTrophies(199)).isEqualTo(Orbit.ORBIT_1);
		assertThat(Orbit.ORBIT_2.englishName()).isEqualTo("Drift Orbit");
		assertThat(Orbit.ORBIT_2.italianName()).isEqualTo("Orbita della Deriva");
		assertThat(Orbit.fromTrophies(200)).isEqualTo(Orbit.ORBIT_2);
		assertThat(Orbit.fromTrophies(499)).isEqualTo(Orbit.ORBIT_2);
		assertThat(Orbit.ORBIT_3.englishName()).isEqualTo("Halo Orbit");
		assertThat(Orbit.ORBIT_3.italianName()).isEqualTo("Orbita dell'Alone");
		assertThat(Orbit.fromTrophies(500)).isEqualTo(Orbit.ORBIT_3);
		assertThat(Orbit.fromTrophies(999)).isEqualTo(Orbit.ORBIT_3);
		assertThat(Orbit.ORBIT_4.englishName()).isEqualTo("Zenith Orbit");
		assertThat(Orbit.ORBIT_4.italianName()).isEqualTo("Orbita dello Zenit");
		assertThat(Orbit.fromTrophies(1000)).isEqualTo(Orbit.ORBIT_4);
	}

	@Test
	void trophiesAboveTheThresholdAreHalved() {
		assertThat(SeasonService.trophiesAfterReset(1200, THRESHOLD)).isEqualTo(1100);
		assertThat(SeasonService.trophiesAfterReset(2000, THRESHOLD)).isEqualTo(1500);
	}

	@Test
	void aHalfIsRoundedUp() {
		assertThat(SeasonService.trophiesAfterReset(1201, THRESHOLD)).isEqualTo(1101);
		assertThat(SeasonService.trophiesAfterReset(1001, THRESHOLD)).isEqualTo(1001);
		assertThat(SeasonService.trophiesAfterReset(1003, THRESHOLD)).isEqualTo(1002);
	}

	@Test
	void summonersAtTheThresholdOrBelowKeepTheirTrophies() {
		assertThat(SeasonService.trophiesAfterReset(1000, THRESHOLD)).isEqualTo(1000);
		assertThat(SeasonService.trophiesAfterReset(999, THRESHOLD)).isEqualTo(999);
		assertThat(SeasonService.trophiesAfterReset(0, THRESHOLD)).isZero();
	}

	@Test
	void aResetNeverAddsTrophies() {
		for (int trophies = 0; trophies <= 5000; trophies++) {
			assertThat(SeasonService.trophiesAfterReset(trophies, THRESHOLD)).isLessThanOrEqualTo(trophies);
		}
	}
}
