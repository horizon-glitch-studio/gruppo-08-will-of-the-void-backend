package com.horizonglitch.wotv.services;

import com.horizonglitch.wotv.exceptions.InsufficientShardsException;
import com.horizonglitch.wotv.exceptions.PackNotFoundException;
import com.horizonglitch.wotv.exceptions.PackUnavailableException;
import com.horizonglitch.wotv.exceptions.PlayerNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.random.RandomGenerator;

@Service
public class PackService {
    private final JdbcTemplate jdbc;
    private final PackDrawEngine engine;

    public PackService(JdbcTemplate jdbc) { this(jdbc, RandomGenerator.getDefault()); }

    PackService(JdbcTemplate jdbc, RandomGenerator random) {
        this.jdbc = jdbc;
        this.engine = new PackDrawEngine(random);
    }

    public record CardReward(long id, String name, int quantity, Instant expiresAt) { }
    public record PackOpening(long id, int packId, String packName, int shardsSpent,
                              int shardBalance, Instant createdAt, List<CardReward> cards) {
        public PackOpening { cards = List.copyOf(cards); }
    }

    /** Debits Shards, rolls, grants cards, and updates pity as one atomic operation. */
    @Transactional
    public PackOpening open(String playerIdentity, int packId) {
        var players = jdbc.queryForList("""
                SELECT id, credit_balance, champion_pack_misses, elect_standard_misses
                FROM player WHERE lower(username)=lower(?) FOR UPDATE
                """, playerIdentity);
        if (players.isEmpty()) throw new PlayerNotFoundException();
        var player = players.getFirst();
        long playerId = ((Number) player.get("id")).longValue();
        int balance = ((Number) player.get("credit_balance")).intValue();
        var packs = jdbc.queryForList("""
                SELECT id, name, credit_cost, pack_kind,
                       champion_guarantee_rarity_id, champion_guarantee_packs,
                       elect_guarantee_rarity_id, elect_guarantee_packs,
                       wanderer_guarantee_rarity_id, wanderer_guarantee_packs,
                       (is_active AND (pack_kind='STANDARD' OR
                        (available_from <= now() AND available_until > now()))) AS available
                FROM pack_type WHERE id=?
                """, packId);
        if (packs.isEmpty()) throw new PackNotFoundException(packId);
        var pack = packs.getFirst();
        if (!Boolean.TRUE.equals(pack.get("available"))) throw new PackUnavailableException(packId);
        int cost = ((Number) pack.get("credit_cost")).intValue();
        if (balance < cost) throw new InsufficientShardsException(balance, cost);
        var kind = PackDrawEngine.PackKind.valueOf((String) pack.get("pack_kind"));
        var champion = configuredGuarantee(pack, "champion_guarantee_rarity_id", "champion_guarantee_packs", true);
        var elect = configuredGuarantee(pack, "elect_guarantee_rarity_id", "elect_guarantee_packs", kind == PackDrawEngine.PackKind.STANDARD);
        var wanderer = configuredGuarantee(pack, "wanderer_guarantee_rarity_id", "wanderer_guarantee_packs", kind == PackDrawEngine.PackKind.EVENT);
        int eventMisses = 0;
        if (kind == PackDrawEngine.PackKind.EVENT) {
            jdbc.update("INSERT INTO player_event_pack_pity(player_id, pack_type_id) VALUES (?, ?) ON CONFLICT DO NOTHING", playerId, packId);
            eventMisses = jdbc.queryForObject("SELECT wanderer_misses FROM player_event_pack_pity WHERE player_id=? AND pack_type_id=? FOR UPDATE", Integer.class, playerId, packId);
        }
        var pity = new PackDrawEngine.Pity(((Number) player.get("champion_pack_misses")).intValue(),
                ((Number) player.get("elect_standard_misses")).intValue(), eventMisses);
        var result = engine.draw(kind, loadSlots(packId), pity, champion, elect, wanderer);

        long openingId;
        var key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO pack_opening(player_id, pack_type_id, credits_spent) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, playerId); ps.setInt(2, packId); ps.setInt(3, cost); return ps;
        }, key);
        openingId = Objects.requireNonNull(key.getKey()).longValue();
        Instant now = Instant.now();
        Map<Long, CardReward> rewards = new LinkedHashMap<>();
        for (var pulled : result.cards()) {
            var card = jdbc.queryForMap("""
                    SELECT c.id, c.name, COALESCE(r.duration_days, 0) duration_days
                    FROM card c JOIN rarity r ON r.id=c.rarity_id
                    WHERE c.is_active AND c.rarity_id=? AND
                      ((r.duration_days IS NULL AND (c.event_pack_type_id IS NULL OR c.event_pack_type_id=?))
                       OR (r.duration_days IS NOT NULL AND c.event_pack_type_id=?))
                    ORDER BY random() LIMIT 1
                    """, pulled.rarity().id(), kind == PackDrawEngine.PackKind.EVENT ? packId : -1,
                    kind == PackDrawEngine.PackKind.EVENT ? packId : -1);
            long cardId = ((Number) card.get("id")).longValue();
            var reward = rewards.get(cardId);
            if (reward == null) rewards.put(cardId, new CardReward(cardId, (String) card.get("name"), 1, null));
            else rewards.put(cardId, new CardReward(cardId, reward.name(), reward.quantity() + 1, null));
        }

        List<CardReward> finalRewards = new ArrayList<>();
        for (var reward : rewards.values()) {
            var duration = jdbc.queryForObject("SELECT COALESCE(r.duration_days, 0) FROM card c JOIN rarity r ON r.id=c.rarity_id WHERE c.id=?", Integer.class, reward.id());
            jdbc.update("INSERT INTO pack_opening_card(pack_opening_id, card_id, quantity) VALUES (?, ?, ?)", openingId, reward.id(), reward.quantity());
            if (duration == 0) {
                jdbc.update("""
                        INSERT INTO collection_card(player_id, card_id, quantity, acquired_at) VALUES (?, ?, ?, ?)
                        ON CONFLICT (player_id, card_id) DO UPDATE SET quantity=collection_card.quantity+EXCLUDED.quantity, acquired_at=EXCLUDED.acquired_at
                        """, playerId, reward.id(), reward.quantity(), now);
                finalRewards.add(reward);
            } else {
                Instant expiresAt = now.plusSeconds(duration * 86400L);
                for (int i = 0; i < reward.quantity(); i++) {
                    jdbc.update("""
                            INSERT INTO collection_card(player_id, card_id, quantity, acquired_at, expires_at) VALUES (?, ?, 1, ?, ?)
                            ON CONFLICT (player_id, card_id) DO UPDATE SET
                              acquired_at=CASE WHEN collection_card.expires_at<=EXCLUDED.acquired_at THEN EXCLUDED.acquired_at ELSE collection_card.acquired_at END,
                              expires_at=CASE WHEN collection_card.expires_at<=EXCLUDED.acquired_at THEN EXCLUDED.expires_at ELSE collection_card.expires_at+interval '7 days' END
                            """, playerId, reward.id(), now, expiresAt);
                    expiresAt = jdbc.queryForObject("SELECT expires_at FROM collection_card WHERE player_id=? AND card_id=?", Instant.class, playerId, reward.id());
                }
                finalRewards.add(new CardReward(reward.id(), reward.name(), 1, expiresAt));
            }
        }
        int newBalance = balance - cost;
        jdbc.update("UPDATE player SET credit_balance=?, champion_pack_misses=?, elect_standard_misses=? WHERE id=?",
                newBalance, result.pity().championMisses(), result.pity().electMisses(), playerId);
        if (kind == PackDrawEngine.PackKind.EVENT)
            jdbc.update("UPDATE player_event_pack_pity SET wanderer_misses=? WHERE player_id=? AND pack_type_id=?", result.pity().wandererMisses(), playerId, packId);
        jdbc.update("INSERT INTO credit_transaction(player_id, amount, reason, pack_opening_id) VALUES (?, ?, 'PACK_PURCHASE', ?)", playerId, -cost, openingId);
        Instant createdAt = jdbc.queryForObject("SELECT created_at FROM pack_opening WHERE id=?", Instant.class, openingId);
        return new PackOpening(openingId, packId, (String) pack.get("name"), cost, newBalance, createdAt, finalRewards);
    }

    private PackDrawEngine.Guarantee configuredGuarantee(Map<String, Object> row, String rarityField, String packsField, boolean required) {
        Object rarityValue = row.get(rarityField), packsValue = row.get(packsField);
        if (rarityValue == null || packsValue == null) {
            if (required) throw new IllegalStateException("Champion guarantee is not configured for pack");
            return new PackDrawEngine.Guarantee(0, Short.MAX_VALUE);
        }
        int rank = jdbc.queryForObject("SELECT sort_order FROM rarity WHERE id=?", Integer.class, ((Number) rarityValue).intValue());
        return new PackDrawEngine.Guarantee(rank, ((Number) packsValue).intValue());
    }

    private List<PackDrawEngine.Slot> loadSlots(int packId) {
        return jdbc.query("""
                SELECT s.position, s.card_count, r.id rarity_id, r.name, r.sort_order, o.probability
                FROM pack_slot s JOIN pack_slot_outcome o USING (pack_type_id, position)
                JOIN rarity r ON r.id=o.rarity_id WHERE s.pack_type_id=? ORDER BY s.position, r.sort_order
                """, rs -> {
            Map<Integer, List<PackDrawEngine.Outcome>> outcomes = new LinkedHashMap<>();
            Map<Integer, Integer> counts = new HashMap<>();
            while (rs.next()) {
                int position = rs.getInt("position");
                counts.put(position, rs.getInt("card_count"));
                outcomes.computeIfAbsent(position, ignored -> new ArrayList<>()).add(new PackDrawEngine.Outcome(
                        new PackDrawEngine.Rarity(rs.getInt("rarity_id"), rs.getString("name"), rs.getInt("sort_order")), rs.getDouble("probability")));
            }
            return outcomes.entrySet().stream().map(e -> new PackDrawEngine.Slot(e.getKey(), counts.get(e.getKey()), e.getValue())).toList();
        }, packId);
    }
}
