package com.horizonglitch.wotv.trade;

/** State of a {@link Trade}. Stored as text; the values match the CHECK constraint of the column. */
public enum TradeStatus {
	PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED, FAILED
}
