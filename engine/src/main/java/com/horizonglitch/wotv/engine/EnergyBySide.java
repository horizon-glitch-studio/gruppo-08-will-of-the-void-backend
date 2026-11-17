package com.horizonglitch.wotv.engine;

/**
 * A value for each side. The components are named after the sides, as {@code energyBySide} in
 * battle-viewer.schema.json, so the record serializes to {@code {"A": .., "B": ..}} without mapping.
 */
public record EnergyBySide(int A, int B) {

	public int of(Side side) {
		return side == Side.A ? A : B;
	}
}
