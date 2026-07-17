-- NPC combat: hit points and an optimistic-locking version.
--
-- The `hit` command makes the player a SECOND writer of an NPC, alongside the
-- autonomous-movement ticker — so the deferred @Version trigger fires (mirroring
-- item, V6): two actors racing to write the same NPC can no longer both win.
-- Hit points are stored as a (current, max) pair; an NPC spawns at full health and
-- a strike lowers `hit_points` (floored at zero). A dead NPC (hit_points = 0) stays
-- in the table but is filtered out of every listing and targeting query.

alter table npc add column hit_points     int;
alter table npc add column max_hit_points int;
alter table npc add column version        bigint;

-- Backfill any NPC already spawned into a local/dev world: full health at a default
-- pool, and version 1 so the next save is an UPDATE (0 = a new, insertable row), the
-- same rationale as item's V6 version backfill.
update npc
set hit_points     = 10,
    max_hit_points = 10,
    version        = 1;

alter table npc alter column hit_points     set not null;
alter table npc alter column max_hit_points set not null;
alter table npc alter column version        set not null;
