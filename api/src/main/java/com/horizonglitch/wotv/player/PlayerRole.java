package com.horizonglitch.wotv.player;

/** Role of a Summoner; Spring Security adds the {@code ROLE_} prefix. Stored as text; the values match the CHECK constraint of the column. */
public enum PlayerRole {
	USER, ADMIN
}
