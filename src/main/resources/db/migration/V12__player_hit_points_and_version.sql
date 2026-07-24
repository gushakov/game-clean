-- Player combat: a hit-point pool and an optimistic-locking version (#66 step 2).
--
-- An NPC counterstrike makes an NPC a WRITER of the player, alongside the player's own
-- `move` — so the player gains @Version (mirroring npc V8 / item V6): two actors racing to
-- write the player can no longer both win. Hit points are stored as a (current, max) pair
-- embedded in the player's own row (the shared HitPointsDbEntity, exactly like npc); the
-- player spawns at full health and an NPC blow lowers `hit_points`, floored at zero.

alter table player add column hit_points     int;
alter table player add column max_hit_points int;
alter table player add column version        bigint;

-- Backfill any player already created in a local/dev world: full health at a default pool,
-- and version 1 so the next save is an UPDATE (0 = a new, insertable row), the same
-- rationale as npc's V8 and item's V6 version backfill.
update player
set hit_points     = 30,
    max_hit_points = 30,
    version        = 1;

alter table player alter column hit_points     set not null;
alter table player alter column max_hit_points set not null;
alter table player alter column version        set not null;
