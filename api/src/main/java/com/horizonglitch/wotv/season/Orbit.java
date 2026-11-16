package com.horizonglitch.wotv.season;

/**
 * The four Orbits, computed from the trophies and never stored (RANK-10). The API calls it {@code league};
 * the names of the Orbits are still to be defined.
 */
public enum Orbit {
	ORBIT_1(0),
	ORBIT_2(200),
	ORBIT_3(500),
	ORBIT_4(1000);

	private final int minTrophies;

	Orbit(int minTrophies) {
		this.minTrophies = minTrophies;
	}

	public int minTrophies() {
		return minTrophies;
	}

	public static Orbit fromTrophies(int trophies) {
		Orbit result = ORBIT_1;
		for (Orbit orbit : values()) {
			if (trophies >= orbit.minTrophies) {
				result = orbit;
			}
		}
		return result;
	}
}
