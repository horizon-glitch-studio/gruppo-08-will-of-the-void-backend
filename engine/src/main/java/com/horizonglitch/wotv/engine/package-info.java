/**
 * Game engine of Will of the Void.
 *
 * <p>Plain Java: it takes the two decks, the mode, a seed and the match parameters ({@link
 * com.horizonglitch.wotv.engine.MatchRequest}) and returns the result and the event log ({@link
 * com.horizonglitch.wotv.engine.MatchResult}). Entry point: {@link com.horizonglitch.wotv.engine.MatchEngine}.
 * It must not depend on Spring, JPA or any I/O, so it can be tested in isolation with JUnit.
 */
package com.horizonglitch.wotv.engine;
