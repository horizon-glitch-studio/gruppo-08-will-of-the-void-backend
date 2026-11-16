-- Will of the Void – reference data from the rules repository
-- (horizon-glitch-studio/gruppo-08-will-of-the-void-docs, folder rules/).
-- Factions and advantages: factions.md. Rarities: cards-and-rarities.md and decks.md.
-- Standard pack: packs.md. Values marked [TEST] in parameters.md may change in a later migration.
-- Cards, starter deck, avatars and events are not here: card-set.md is still [TBD].
-- Rows refer to each other by name, never by generated id. Do not edit after it has been applied.

-- Factions (factions.md): six elemental factions and two inert ones.
INSERT INTO faction (name, is_inert) VALUES
  ('Lava', false),
  ('Electro', false),
  ('Radio', false),
  ('Ice', false),
  ('Metal', false),
  ('Matte', false),
  ('Gem', true),
  ('Noble Gas', true);

-- The twelve advantages (factions.md, FAC-01 to FAC-03): every elemental faction prevails over two others.
-- Advantages work only in attack: +3 ATK (ADVANTAGE_VALUE, [TEST]).
INSERT INTO faction_advantage (dominant_faction_id, dominated_faction_id, type, value)
SELECT dominant.id, dominated.id, 'ATTACK', 3
FROM (VALUES
  ('Lava', 'Metal'),
  ('Lava', 'Matte'),
  ('Electro', 'Lava'),
  ('Electro', 'Ice'),
  ('Radio', 'Lava'),
  ('Radio', 'Matte'),
  ('Ice', 'Radio'),
  ('Ice', 'Metal'),
  ('Metal', 'Electro'),
  ('Metal', 'Radio'),
  ('Matte', 'Electro'),
  ('Matte', 'Ice')
) AS pair (dominant_name, dominated_name)
JOIN faction dominant ON dominant.name = pair.dominant_name
JOIN faction dominated ON dominated.name = pair.dominated_name;

-- Rarities, from the most common to the rarest (cards-and-rarities.md, decks.md).
-- total_points: ATK + DEF (POINTS_*). max_copies_per_deck: copies of the same card (DECK-02).
-- max_per_deck: cards of the rarity in a deck, NULL = no limit (DECK-03).
-- duration_days: days on board, NULL = permanent (WANDERER_DURATION_DAYS). Wanderer cards cannot be traded (CARD-17).
-- The backend finds Champion, Elect and Wanderer by name for the pack guarantees (app.packs.*-rarity).
INSERT INTO rarity (name, sort_order, total_points, max_copies_per_deck, max_per_deck, duration_days, is_tradeable) VALUES
  ('Captive', 1, 12, 3, NULL, NULL, true),
  ('Conscript', 2, 16, 3, NULL, NULL, true),
  ('Soldier', 3, 20, 2, 8, NULL, true),
  ('Champion', 4, 24, 1, 3, NULL, true),
  ('Elect', 5, 28, 1, 2, NULL, true),
  ('Wanderer', 6, 32, 1, 1, 7, false);

-- The standard pack (packs.md, PACK-02, PACK-04): always on sale, 300 Shards (PACK_PRICE).
INSERT INTO pack_type (name, kind, shard_cost) VALUES ('Standard pack', 'STANDARD', 300);

INSERT INTO pack_slot (pack_type_id, position)
SELECT pack.id, slot.position
FROM pack_type pack
CROSS JOIN generate_series(1, 5) AS slot (position)
WHERE pack.name = 'Standard pack' AND pack.kind = 'STANDARD';

-- Rarity probabilities of each slot of the standard pack, in percent (PACK-05). Every slot adds up to 100.
INSERT INTO pack_slot_rarity (pack_type_id, position, rarity_id, probability)
SELECT pack.id, odds.position, rarity.id, odds.probability
FROM (VALUES
  (1, 'Captive', 70.00),
  (1, 'Conscript', 30.00),
  (2, 'Captive', 70.00),
  (2, 'Conscript', 30.00),
  (3, 'Captive', 70.00),
  (3, 'Conscript', 30.00),
  (4, 'Conscript', 75.00),
  (4, 'Soldier', 25.00),
  (5, 'Soldier', 84.00),
  (5, 'Champion', 12.50),
  (5, 'Elect', 3.50)
) AS odds (position, rarity_name, probability)
JOIN rarity ON rarity.name = odds.rarity_name
CROSS JOIN pack_type pack
WHERE pack.name = 'Standard pack' AND pack.kind = 'STANDARD';
