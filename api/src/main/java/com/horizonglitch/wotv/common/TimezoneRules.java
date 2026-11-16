package com.horizonglitch.wotv.common;

import java.time.ZoneId;

/** The time zone of a Summoner is an IANA region id, such as Europe/Rome. */
public final class TimezoneRules {

	private TimezoneRules() {
	}

	/** Offsets such as +01:00 are refused, because they do not follow daylight saving time. */
	public static boolean isIana(String timezone) {
		return timezone != null && timezone.contains("/") && ZoneId.getAvailableZoneIds().contains(timezone);
	}
}
