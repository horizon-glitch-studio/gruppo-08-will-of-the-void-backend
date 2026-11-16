package com.horizonglitch.wotv.services;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horizonglitch.wotv.exceptions.ApiException;
import com.horizonglitch.wotv.exceptions.ApiException.FieldViolation;
import com.horizonglitch.wotv.payload.response.ApiPage;
import com.horizonglitch.wotv.payload.response.ArchivedCardView;
import com.horizonglitch.wotv.payload.response.CardView;
import com.horizonglitch.wotv.payload.response.CollectionCardView;

@Service
public class CollectionService {

	private static final String CARD_COLUMNS = """
			c.id AS card_id, c.name AS card_name, c.description, c.image_url,
			c.attack, c.defense, f.id AS faction_id, f.name AS faction_name,
			r.id AS rarity_id, r.name AS rarity_name, r.sort_order,
			r.max_copies_per_deck, r.max_per_deck
			""";

	private final JdbcTemplate jdbcTemplate;
	private final PlayerIdentityService playerIdentityService;

	public CollectionService(JdbcTemplate jdbcTemplate, PlayerIdentityService playerIdentityService) {
		this.jdbcTemplate = jdbcTemplate;
		this.playerIdentityService = playerIdentityService;
	}

	@Transactional
	public ApiPage<CollectionCardView> getCollection(
			String username,
			int page,
			int size,
			Integer factionId,
			Integer rarityId,
			Boolean availableInDeck) {
		validatePage(page, size);
		long playerId = playerIdentityService.requirePlayerId(username);
		expireForPlayer(playerId);
		StringBuilder where = new StringBuilder(" WHERE cc.player_id = ?");
		List<Object> parameters = new ArrayList<>();
		parameters.add(playerId);

		if (factionId != null) {
			where.append(" AND c.faction_id = ?");
			parameters.add(factionId);
		}
		if (rarityId != null) {
			where.append(" AND c.rarity_id = ?");
			parameters.add(rarityId);
		}
		if (availableInDeck != null) {
			where.append(availableInDeck
					? " AND (cc.expires_at IS NULL OR cc.expires_at > CURRENT_TIMESTAMP)"
					: " AND cc.expires_at <= CURRENT_TIMESTAMP");
		}

		Long total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM collection_card cc JOIN card c ON c.id = cc.card_id" + where,
				Long.class,
				parameters.toArray());
		parameters.add(size);
		parameters.add((long) page * size);
		List<CollectionCardView> items = jdbcTemplate.query(
				"SELECT cc.quantity, cc.acquired_at, cc.expires_at, " + CARD_COLUMNS
						+ " FROM collection_card cc"
						+ " JOIN card c ON c.id = cc.card_id"
						+ " JOIN faction f ON f.id = c.faction_id"
						+ " JOIN rarity r ON r.id = c.rarity_id"
						+ where
						+ " ORDER BY c.id LIMIT ? OFFSET ?",
				(rs, rowNum) -> new CollectionCardView(
						toCardView(rs),
						rs.getInt("quantity"),
						toInstant(rs.getTimestamp("acquired_at")),
						toInstant(rs.getTimestamp("expires_at")),
						isAvailable(rs.getTimestamp("expires_at"))),
				parameters.toArray());
		return page(items, page, size, total == null ? 0 : total);
	}

	@Transactional
	public CollectionCardView getCollectionCard(String username, long cardId) {
		long playerId = playerIdentityService.requirePlayerId(username);
		expireForPlayer(playerId);
		List<CollectionCardView> cards = jdbcTemplate.query(
				"SELECT cc.quantity, cc.acquired_at, cc.expires_at, " + CARD_COLUMNS
						+ " FROM collection_card cc"
						+ " JOIN card c ON c.id = cc.card_id"
						+ " JOIN faction f ON f.id = c.faction_id"
						+ " JOIN rarity r ON r.id = c.rarity_id"
						+ " WHERE cc.player_id = ? AND cc.card_id = ?",
				(rs, rowNum) -> new CollectionCardView(
						toCardView(rs),
						rs.getInt("quantity"),
						toInstant(rs.getTimestamp("acquired_at")),
						toInstant(rs.getTimestamp("expires_at")),
						isAvailable(rs.getTimestamp("expires_at"))),
				playerId,
				cardId);
		if (cards.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "The card is not in this player's collection.");
		}
		return cards.getFirst();
	}

	@Transactional
	public ApiPage<ArchivedCardView> getArchive(String username, int page, int size) {
		validatePage(page, size);
		long playerId = playerIdentityService.requirePlayerId(username);
		expireForPlayer(playerId);
		Long total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM memory_archive WHERE player_id = ?",
				Long.class,
				playerId);
		List<ArchivedCardView> items = jdbcTemplate.query(
				"SELECT ma.id AS archive_id, ma.arrived_at, ma.departed_at, " + CARD_COLUMNS
						+ " FROM memory_archive ma"
						+ " JOIN card c ON c.id = ma.card_id"
						+ " JOIN faction f ON f.id = c.faction_id"
						+ " JOIN rarity r ON r.id = c.rarity_id"
						+ " WHERE ma.player_id = ?"
						+ " ORDER BY ma.departed_at DESC, ma.id DESC LIMIT ? OFFSET ?",
				(rs, rowNum) -> new ArchivedCardView(
						rs.getLong("archive_id"),
						toCardView(rs),
						toInstant(rs.getTimestamp("arrived_at")),
						toInstant(rs.getTimestamp("departed_at"))),
				playerId,
				size,
				(long) page * size);
		return page(items, page, size, total == null ? 0 : total);
	}

	@Transactional
	public void expireForPlayer(long playerId) {
		List<ExpiredCard> expiredCards = jdbcTemplate.query(
				"SELECT card_id, acquired_at, expires_at FROM collection_card"
						+ " WHERE player_id = ? AND expires_at <= CURRENT_TIMESTAMP"
						+ " ORDER BY expires_at FOR UPDATE",
				(rs, rowNum) -> new ExpiredCard(
						rs.getLong("card_id"),
						rs.getTimestamp("acquired_at"),
						rs.getTimestamp("expires_at")),
				playerId);
		for (ExpiredCard expired : expiredCards) {
			jdbcTemplate.update(
					"INSERT INTO memory_archive (player_id, card_id, arrived_at, departed_at) VALUES (?, ?, ?, ?)",
					playerId,
					expired.cardId(),
					expired.acquiredAt(),
					expired.expiresAt());
			jdbcTemplate.update(
					"UPDATE deck SET status = 'INVALID' WHERE player_id = ? AND id IN"
							+ " (SELECT deck_id FROM deck_card WHERE card_id = ?)",
					playerId,
					expired.cardId());
			jdbcTemplate.update(
					"DELETE FROM collection_card WHERE player_id = ? AND card_id = ?",
					playerId,
					expired.cardId());
		}
	}

	private void validatePage(int page, int size) {
		List<FieldViolation> errors = new ArrayList<>();
		if (page < 0) {
			errors.add(new FieldViolation("page", "VALUE_MUST_BE_NON_NEGATIVE"));
		}
		if (size < 1 || size > 100) {
			errors.add(new FieldViolation("size", "PAGE_SIZE_OUT_OF_RANGE"));
		}
		if (!errors.isEmpty()) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"VALIDATION_FAILED",
					"Page must be non-negative and size must be between 1 and 100.",
					errors);
		}
	}

	private <T> ApiPage<T> page(List<T> items, int page, int size, long total) {
		long totalPages = total == 0 ? 0 : (total + size - 1) / size;
		return new ApiPage<>(items, page, size, total, totalPages);
	}

	private CardView toCardView(ResultSet rs) throws SQLException {
		Integer maxPerDeck = (Integer) rs.getObject("max_per_deck");
		return new CardView(
				rs.getLong("card_id"),
				rs.getString("card_name"),
				rs.getString("description"),
				rs.getString("image_url"),
				new CardView.FactionView(rs.getInt("faction_id"), rs.getString("faction_name")),
				new CardView.RarityView(
						rs.getInt("rarity_id"),
						rs.getString("rarity_name"),
						rs.getInt("sort_order"),
						rs.getInt("max_copies_per_deck"),
						maxPerDeck),
				rs.getInt("attack"),
				rs.getInt("defense"));
	}

	private java.time.Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	private boolean isAvailable(Timestamp expiresAt) {
		return expiresAt == null || expiresAt.toInstant().isAfter(java.time.Instant.now());
	}

	private record ExpiredCard(long cardId, Timestamp acquiredAt, Timestamp expiresAt) {
	}
}
