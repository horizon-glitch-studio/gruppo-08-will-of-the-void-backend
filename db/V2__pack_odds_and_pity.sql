-- Extend the initial pack schema without changing the already-applied V1 migration.
ALTER TABLE pack_type
    ADD COLUMN pack_kind varchar(10) NOT NULL DEFAULT 'STANDARD'
        CHECK (pack_kind IN ('STANDARD', 'EVENT')),
    ADD COLUMN champion_guarantee_rarity_id int REFERENCES rarity(id),
    ADD COLUMN champion_guarantee_packs smallint CHECK (champion_guarantee_packs > 0),
    ADD COLUMN elect_guarantee_rarity_id int REFERENCES rarity(id),
    ADD COLUMN elect_guarantee_packs smallint CHECK (elect_guarantee_packs > 0),
    ADD COLUMN wanderer_guarantee_rarity_id int REFERENCES rarity(id),
    ADD COLUMN wanderer_guarantee_packs smallint CHECK (wanderer_guarantee_packs > 0),
    ADD CONSTRAINT chk_pack_guarantee_pairs CHECK (
        (champion_guarantee_rarity_id IS NULL) = (champion_guarantee_packs IS NULL)
        AND (elect_guarantee_rarity_id IS NULL) = (elect_guarantee_packs IS NULL)
        AND (wanderer_guarantee_rarity_id IS NULL) = (wanderer_guarantee_packs IS NULL)
    ),
    ADD CONSTRAINT chk_pack_kind_guarantees CHECK (
        (pack_kind = 'STANDARD' AND wanderer_guarantee_rarity_id IS NULL)
        OR (pack_kind = 'EVENT' AND elect_guarantee_rarity_id IS NULL)
    );

-- Different rarity outcomes can now be configured for every slot. Keep the old
-- base/upgrade columns for compatibility while migrating their values here.
CREATE TABLE pack_slot_outcome (
    pack_type_id int NOT NULL,
    position smallint NOT NULL,
    rarity_id int NOT NULL,
    probability numeric(5,2) NOT NULL CHECK (probability > 0 AND probability <= 100),
    PRIMARY KEY (pack_type_id, position, rarity_id),
    FOREIGN KEY (pack_type_id, position) REFERENCES pack_slot(pack_type_id, position) ON DELETE CASCADE,
    FOREIGN KEY (rarity_id) REFERENCES rarity(id)
);

INSERT INTO pack_slot_outcome (pack_type_id, position, rarity_id, probability)
SELECT ps.pack_type_id, ps.position, ps.rarity_id,
       CASE WHEN ps.upgrade_chance IS NULL THEN 100 ELSE 100 - ps.upgrade_chance END
FROM pack_slot ps
WHERE ps.upgrade_chance IS NULL OR ps.upgrade_chance < 100;

INSERT INTO pack_slot_outcome (pack_type_id, position, rarity_id, probability)
SELECT pack_type_id, position, upgrade_rarity_id, upgrade_chance
FROM pack_slot
WHERE upgrade_chance IS NOT NULL;

ALTER TABLE player
    ADD COLUMN champion_pack_misses int NOT NULL DEFAULT 0 CHECK (champion_pack_misses >= 0),
    ADD COLUMN elect_standard_misses int NOT NULL DEFAULT 0 CHECK (elect_standard_misses >= 0);

-- Wanderer pity belongs to an event pack, so starting another event starts a
-- separate counter while the standard and Champion counters persist.
CREATE TABLE player_event_pack_pity (
    player_id bigint NOT NULL REFERENCES player(id) ON DELETE CASCADE,
    pack_type_id int NOT NULL REFERENCES pack_type(id) ON DELETE CASCADE,
    wanderer_misses int NOT NULL DEFAULT 0 CHECK (wanderer_misses >= 0),
    PRIMARY KEY (player_id, pack_type_id)
);

-- A card assigned to an event pack can only be drawn from that event. Cards
-- with no event assignment remain eligible for standard packs.
ALTER TABLE card ADD COLUMN event_pack_type_id int REFERENCES pack_type(id) ON DELETE SET NULL;
CREATE INDEX ix_card_event_pool ON card (event_pack_type_id, rarity_id) WHERE is_active;

CREATE FUNCTION enforce_event_wanderer_pool() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM rarity r
        JOIN pack_type p ON p.id = NEW.pack_type_id
        WHERE r.id = NEW.rarity_id AND r.name = 'Wanderer' AND p.pack_kind <> 'EVENT'
    ) THEN
        RAISE EXCEPTION 'Wanderer outcomes are only allowed in event packs';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_event_wanderer_pack_slot
    BEFORE INSERT OR UPDATE ON pack_slot_outcome
    FOR EACH ROW EXECUTE FUNCTION enforce_event_wanderer_pool();

-- Prevent overlapping event sales, including concurrent admin changes.
ALTER TABLE pack_type ADD CONSTRAINT ex_one_event_pack_on_sale
    EXCLUDE USING gist (
        tstzrange(coalesce(available_from, '-infinity'::timestamptz),
                  coalesce(available_until, 'infinity'::timestamptz), '[)') WITH &&
    ) WHERE (is_active AND pack_kind = 'EVENT');

ALTER TABLE pack_slot DROP CONSTRAINT IF EXISTS chk_slot_upgrade_complete;
ALTER TABLE pack_slot DROP CONSTRAINT IF EXISTS chk_slot_upgrade_different_rarity;
ALTER TABLE pack_slot DROP COLUMN upgrade_rarity_id;
ALTER TABLE pack_slot DROP COLUMN upgrade_chance;
