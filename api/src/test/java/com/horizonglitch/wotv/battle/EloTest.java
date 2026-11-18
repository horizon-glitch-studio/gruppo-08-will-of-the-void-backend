package com.horizonglitch.wotv.battle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RANK-03 and RANK-04 with ELO_K = 32. */
class EloTest {

	private static final int K = 32;

	@Test
	void anEvenMatchMovesSixteenTrophies() {
		assertThat(Elo.delta(1000, 1000, Elo.WIN, K)).isEqualTo(16);
		assertThat(Elo.delta(1000, 1000, Elo.DEFEAT, K)).isEqualTo(-16);
	}

	@Test
	void aDrawBetweenEqualsMovesNothing() {
		assertThat(Elo.delta(500, 500, Elo.DRAW, K)).isZero();
	}

	@Test
	void beatingAStrongerSummonerGivesMoreAndAWeakerOneLess() {
		// expected result of the weaker one: 1 / (1 + 10^(200 / 400)) = 0.24
		assertThat(Elo.delta(1000, 1200, Elo.WIN, K)).isEqualTo(24);
		assertThat(Elo.delta(1200, 1000, Elo.WIN, K)).isEqualTo(8);
		assertThat(Elo.delta(1200, 1000, Elo.DEFEAT, K)).isEqualTo(-24);
	}

	@Test
	void aDrawCountsAsHalfAWin() {
		assertThat(Elo.delta(1000, 1200, Elo.DRAW, K)).isEqualTo(8);
		assertThat(Elo.delta(1200, 1000, Elo.DRAW, K)).isEqualTo(-8);
	}

	@Test
	void whatOneGainsTheOtherLoses() {
		for (int a = 0; a <= 2000; a += 137) {
			for (int b = 0; b <= 2000; b += 211) {
				assertThat(Elo.delta(a, b, Elo.WIN, K)).isEqualTo(-Elo.delta(b, a, Elo.DEFEAT, K));
			}
		}
	}
}
