package com.horizonglitch.wotv.trade;

/** Direction of a {@link TradeCard}: OFFER goes to the recipient, REQUEST to the proposer. Stored as text; the values match the CHECK constraint of the column. */
public enum TradeSide {
	OFFER, REQUEST
}
