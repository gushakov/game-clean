-- NPC corpse ref (issue #93): a slain NPC may leave a corpse — an anchored container item
-- minted where it fell, holding rolled loot.
--
-- `corpse_ref` is the AUTHORED HANDLE of the corpse's item template (the `id:` of an
-- `items:` entry in the seed), copied onto every spawned instance — the first authored
-- handle persisted on an aggregate row. It is deliberately NOT an item instance id and NOT
-- a foreign key: no corpse instance exists until the death mints one, and whether the ref
-- still resolves against the current seed is the corpse-blueprint adapter's business at
-- death time (drift after an edited seed surfaces as an integrity fault there).
--
-- Nullable on purpose: an NPC authored without `corpse:` leaves no corpse. Existing rows
-- (worlds seeded before this migration) stay null — slaying them just deletes the row.

alter table npc
    add column corpse_ref varchar(64);
