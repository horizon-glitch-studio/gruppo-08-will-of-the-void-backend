package com.horizonglitch.wotv.season;

/**
 * The four Orbits, computed from the trophies and never stored (RANK-10). The API calls it {@code league};
 * and keeps the stable code (ORBIT_1 to ORBIT_4) separate from the player-facing names.
 */
public enum Orbit {
	ORBIT_1(0, "Brink Orbit", "Orbita del Bordo"),
	ORBIT_2(200, "Drift Orbit", "Orbita della Deriva"),
	ORBIT_3(500, "Halo Orbit", "Orbita dell'Alone"),
	ORBIT_4(1000, "Zenith Orbit", "Orbita dello Zenit");

	private final int minTrophies;
	private final String englishName;
	private final String italianName;

	Orbit(int minTrophies, String englishName, String italianName) {
		this.minTrophies = minTrophies;
		this.englishName = englishName;
		this.italianName = italianName;
	}

	public int minTrophies() {
		return minTrophies;
	}

	public String englishName() {
		return englishName;
	}

	public String italianName() {
		return italianName;
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
