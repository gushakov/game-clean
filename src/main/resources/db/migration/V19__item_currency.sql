-- Currency (issue #117): an item may be money — one instance counts as one coin toward a price.
--
-- The world has ONE currency and no denominations, so the fact is a bare flag rather than a
-- value object: every currency item counts as one coin, whatever its template. The flag is inert
-- in this phase — nothing counts or spends coins yet — and is carried by the template onto every
-- spawned instance, like `container` and `opens` (there is no persisted template to read from).
--
-- The domain invariant (a currency item is neither a container nor anchored) is the construction
-- gate's, checked on every boot by the seed gate, not a database constraint — the same division
-- that keeps `max_hit_points > 0` out of the schema.
--
-- Existing rows default to ordinary property; no backfill. A play database seeded before this
-- migration keeps its already-spawned coins as plain loot — reset the play volume to pick up the
-- authored fact.

alter table item
    add column currency boolean not null default false;
