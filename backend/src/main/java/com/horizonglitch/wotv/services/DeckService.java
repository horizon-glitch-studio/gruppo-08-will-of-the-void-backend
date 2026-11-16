package com.horizonglitch.wotv.services;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horizonglitch.wotv.exceptions.ApiException;
import com.horizonglitch.wotv.exceptions.ApiException.FieldViolation;
import com.horizonglitch.wotv.payload.response.CardView;
import com.horizonglitch.wotv.payload.response.DeckApiModels.CardSelection;
import com.horizonglitch.wotv.payload.response.DeckApiModels.DeckCardView;
import com.horizonglitch.wotv.payload.response.DeckApiModels.DeckView;
import com.horizonglitch.wotv.payload.response.DeckApiModels.SaveDeckRequest;
import com.horizonglitch.wotv.payload.response.DeckApiModels.ValidationResult;

@Service
public class DeckService {

	private static final int MAX_DECKS_PER_PLAYER = 20;

	private static final String CARD_COLUMNS = """
			c.id AS card_id, c.name AS card_name, c.description, c.image_url,
			c.attack, c.defense, f.id AS faction_id, f.name AS faction_name,
			f.is_inert AS faction_is_inert, r.id AS rarity_id, r.name AS rarity_name,
			r.sort_order, r.max_copies_per_deck, r.max_per_deck,
			COALESCE(cc.quantity, 0) AS owned_quantity,
			(cc.card_id IS NOT NULL AND (cc.expires_at IS NULL OR cc.expires_at > CURRENT_TIMESTAMP)) AS available
			""";

	private final JdbcTemplate jdbcTemplate;
	private final PlayerIdentityService playerIdentityService;
	private final DeckValidationService deckValidationService;
	private final CollectionService collectionService;

	public DeckService(
			JdbcTemplate jdbcTemplate,
			PlayerIdentityService playerIdentityService,
			DeckValidationService deckValidationService,
			CollectionService collectionService) {
		this.jdbcTemplate = jdbcTemplate;
		this.playerIdentityService = playerIdentityService;
		this.deckValidationService = deckValidationService;
		this.collectionService = collectionService;
	}

	@Transactional
	public List<DeckView> getDecks(String username) {
		long playerId = resolvePlayerId(username);
		Long defenseDeckId = getDefenseDeckId(playerId);
		List<DeckRecord> decks = jdbcTemplate.query(
				"SELECT id, name, status, updated_at FROM deck WHERE player_id = ? ORDER BY id",
				this::toDeckRecord,
				playerId);
		return decks.stream()
				.map(deck -> toDeckView(playerId, deck, defenseDeckId))
				.toList();
	}

	@Transactional
	public DeckView getDeck(String username, long deckId) {
		long playerId = resolvePlayerId(username);
		return toDeckView(playerId, requireDeck(playerId, deckId), getDefenseDeckId(playerId));
	}

	@Transactional
	public DeckView createDeck(String username, SaveDeckRequest request) {
		long playerId = resolvePlayerId(username);
		lockPlayer(playerId);
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM deck WHERE player_id = ?",
				Long.class,
				playerId);
		if (count != null && count >= MAX_DECKS_PER_PLAYER) {
			throw new ApiException(HttpStatus.CONFLICT, "DECK_LIMIT_REACHED", "A player can save at most 20 decks.");
		}
		ensureUniqueName(playerId, request.name(), null);
		ValidationResult validation = validate(playerId, request.cards());
		Long deckId = insertDeck(playerId, request.name(), validation.status());
		replaceCards(deckId, request.cards());
		return toDeckView(playerId, requireDeck(playerId, deckId), getDefenseDeckId(playerId));
	}

	@Transactional
	public DeckView updateDeck(String username, long deckId, SaveDeckRequest request) {
		long playerId = resolvePlayerId(username);
		lockPlayer(playerId);
		requireDeck(playerId, deckId);
		ensureUniqueName(playerId, request.name(), deckId);
		ValidationResult validation = validate(playerId, request.cards());
		jdbcTemplate.update(
				"UPDATE deck SET name = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND player_id = ?",
				request.name(),
				validation.status(),
				deckId,
				playerId);
		jdbcTemplate.update("DELETE FROM deck_card WHERE deck_id = ?", deckId);
		replaceCards(deckId, request.cards());
		return toDeckView(playerId, requireDeck(playerId, deckId), getDefenseDeckId(playerId));
	}

	@Transactional
	public void deleteDeck(String username, long deckId) {
		long playerId = resolvePlayerId(username);
		lockPlayer(playerId);
		requireDeck(playerId, deckId);
		jdbcTemplate.update("DELETE FROM deck WHERE id = ? AND player_id = ?", deckId, playerId);
	}

	@Transactional
	public DeckView activateDefenseDeck(String username, long deckId) {
		long playerId = resolvePlayerId(username);
		lockPlayer(playerId);
		DeckRecord deck = requireDeck(playerId, deckId);
		DeckView current = toDeckView(playerId, deck, getDefenseDeckId(playerId));
		if (!"VALID".equals(current.status())) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"DEFENSE_DECK_INVALID",
					"Only a valid deck can be selected as the defensive deck.");
		}
		jdbcTemplate.update("UPDATE player SET defense_deck_id = ? WHERE id = ?", deckId, playerId);
		return toDeckView(playerId, requireDeck(playerId, deckId), deckId);
	}

	@Transactional
	public void deactivateDefenseDeck(String username, long deckId) {
		long playerId = resolvePlayerId(username);
		lockPlayer(playerId);
		requireDeck(playerId, deckId);
		jdbcTemplate.update(
				"UPDATE player SET defense_deck_id = NULL WHERE id = ? AND defense_deck_id = ?",
				playerId,
				deckId);
	}

	@Transactional
	public ValidationResult validateDeck(String username, List<CardSelection> selections) {
		long playerId = resolvePlayerId(username);
		return validate(playerId, selections);
	}

	private ValidationResult validate(long playerId, List<CardSelection> selections) {
		requireUniqueCardIds(selections);
		if (selections.isEmpty()) {
			return deckValidationService.validate(selections, Map.of());
		}

		List<Long> cardIds = selections.stream()
				.map((@NonNull CardSelection selection) -> selection.cardId())
				.toList();
		Map<Long, LoadedCard> cards = loadCards(playerId, cardIds);
		for (Long cardId : cardIds) {
			if (!cards.containsKey(cardId)) {
				throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Card " + cardId + " was not found.");
			}
		}
		Map<Long, DeckValidationService.CardRule> rules = new HashMap<>();
		cards.forEach((cardId, card) -> rules.put(cardId, card.rule()));
		return deckValidationService.validate(selections, rules);
	}

	private void requireUniqueCardIds(List<CardSelection> selections) {
		Set<Long> seen = new HashSet<>();
		for (CardSelection selection : selections) {
			if (!seen.add(selection.cardId())) {
				throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"VALIDATION_FAILED",
						"Card IDs must be unique in a deck.",
						List.of(new FieldViolation("cards", "DUPLICATE_CARD_ID")));
			}
		}
	}

	private DeckView toDeckView(long playerId, DeckRecord deck, Long defenseDeckId) {
		List<CardSelection> selections = jdbcTemplate.query(
				"SELECT card_id, quantity FROM deck_card WHERE deck_id = ? ORDER BY card_id",
				(rs, rowNum) -> new CardSelection(rs.getLong("card_id"), rs.getInt("quantity")),
				deck.id());
		Map<Long, LoadedCard> cards = selections.isEmpty()
				? Map.of()
				: loadCards(
						playerId,
						selections.stream()
								.map((@NonNull CardSelection selection) -> selection.cardId())
								.toList());
		Map<Long, DeckValidationService.CardRule> rules = new HashMap<>();
		cards.forEach((cardId, card) -> rules.put(cardId, card.rule()));
		ValidationResult validation = deckValidationService.validate(selections, rules);
		jdbcTemplate.update(
				"UPDATE deck SET status = ? WHERE id = ? AND status <> ?",
				validation.status(),
				deck.id(),
				validation.status());
		List<DeckCardView> deckCards = selections.stream()
				.map(selection -> new DeckCardView(
						cards.get(selection.cardId()).view(),
						selection.quantity()))
				.toList();
		return new DeckView(
				deck.id(),
				deck.name(),
				validation.status(),
				deckCards,
				validation.totalCards(),
				defenseDeckId != null && defenseDeckId == deck.id() && "VALID".equals(validation.status()),
				deck.updatedAt(),
				validation.validationErrors());
	}

	private Map<Long, LoadedCard> loadCards(long playerId, List<Long> cardIds) {
		String placeholders = String.join(",", java.util.Collections.nCopies(cardIds.size(), "?"));
		List<Object> parameters = new ArrayList<>();
		parameters.add(playerId);
		parameters.addAll(cardIds);
		List<LoadedCard> loaded = jdbcTemplate.query(
				"SELECT " + CARD_COLUMNS
						+ " FROM card c"
						+ " JOIN faction f ON f.id = c.faction_id"
						+ " JOIN rarity r ON r.id = c.rarity_id"
						+ " LEFT JOIN collection_card cc ON cc.card_id = c.id AND cc.player_id = ?"
						+ " WHERE c.id IN (" + placeholders + ")",
				this::toLoadedCard,
				parameters.toArray());
		Map<Long, LoadedCard> byId = new HashMap<>();
		for (LoadedCard card : loaded) {
			byId.put(card.view().id(), card);
		}
		return byId;
	}

	private LoadedCard toLoadedCard(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
		Integer maxPerDeck = (Integer) rs.getObject("max_per_deck");
		CardView view = new CardView(
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
		return new LoadedCard(
				view,
				new DeckValidationService.CardRule(
						rs.getInt("rarity_id"),
						rs.getString("rarity_name"),
						rs.getInt("faction_id"),
						rs.getBoolean("faction_is_inert"),
						rs.getInt("max_copies_per_deck"),
						maxPerDeck,
						rs.getInt("owned_quantity"),
						rs.getBoolean("available")));
	}

	private DeckRecord requireDeck(long playerId, long deckId) {
		List<DeckRecord> records = jdbcTemplate.query(
				"SELECT id, name, status, updated_at FROM deck WHERE id = ? AND player_id = ?",
				this::toDeckRecord,
				deckId,
				playerId);
		if (records.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "DECK_NOT_FOUND", "Deck was not found.");
		}
		return records.getFirst();
	}

	private DeckRecord toDeckRecord(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
		Timestamp updatedAt = rs.getTimestamp("updated_at");
		return new DeckRecord(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("status"),
				updatedAt == null ? null : updatedAt.toInstant());
	}

	private Long getDefenseDeckId(long playerId) {
		List<Long> ids = jdbcTemplate.query(
				"SELECT defense_deck_id FROM player WHERE id = ?",
				(rs, rowNum) -> {
					long id = rs.getLong("defense_deck_id");
					return rs.wasNull() ? null : id;
				},
				playerId);
		return ids.isEmpty() ? null : ids.getFirst();
	}

	private void ensureUniqueName(long playerId, String name, Long exceptDeckId) {
		String sql = "SELECT COUNT(*) FROM deck WHERE player_id = ? AND name = ?"
				+ (exceptDeckId == null ? "" : " AND id <> ?");
		Long count = exceptDeckId == null
				? jdbcTemplate.queryForObject(sql, Long.class, playerId, name)
				: jdbcTemplate.queryForObject(sql, Long.class, playerId, name, exceptDeckId);
		if (count != null && count > 0) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"DECK_NAME_ALREADY_USED",
					"A deck with this name already exists.");
		}
	}

	private void lockPlayer(long playerId) {
		jdbcTemplate.queryForObject("SELECT id FROM player WHERE id = ? FOR UPDATE", Long.class, playerId);
	}

	private long resolvePlayerId(String username) {
		long playerId = playerIdentityService.requirePlayerId(username);
		collectionService.expireForPlayer(playerId);
		return playerId;
	}

	private Long insertDeck(long playerId, String name, String status) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
					"INSERT INTO deck (player_id, name, status) VALUES (?, ?, ?)",
					new String[] { "id" });
			statement.setLong(1, playerId);
			statement.setString(2, name);
			statement.setString(3, status);
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("The database did not return the new deck ID.");
		}
		return key.longValue();
	}

	private void replaceCards(long deckId, List<CardSelection> cards) {
		for (CardSelection selection : cards) {
			jdbcTemplate.update(
					"INSERT INTO deck_card (deck_id, card_id, quantity) VALUES (?, ?, ?)",
					deckId,
					selection.cardId(),
					selection.quantity());
		}
	}

	private record LoadedCard(CardView view, DeckValidationService.CardRule rule) {
	}

	private record DeckRecord(long id, String name, String status, Instant updatedAt) {
	}
}