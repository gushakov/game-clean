-- Item gains a mobile location and an optimistic-locking version, for `take`.
--
-- Before `take`, an item's location was a single scene_id (an item was always on the
-- ground). `take` makes location mobile (ground <-> held by a player), so the domain's
-- sealed Location VO is flattened to a (location_kind, location_ref) pair: kind is
-- 'GROUND' (ref is a scene id) or 'HELD' (ref is a holder/player id). Neither ref is a
-- foreign key, mirroring exit.target_scene_id and player.current_scene_id.
--
-- `take` is also the first select-then-mutate on a contested resource (any actor in a
-- scene can grab a ground item), so a version column closes the take-vs-take race via
-- Spring Data JDBC optimistic locking, exactly as day_phase_log does (V5).

-- Add the new columns nullable first, so the backfill can populate them before NOT NULL.
alter table item add column location_kind varchar(16);
alter table item add column location_ref  varchar(64);
alter table item add column version       bigint;

-- Backfill existing rows: every current item is on the ground in its former scene.
-- version = 1 marks them as already-persisted so Spring's version-based new-vs-existing
-- check treats the next save as an UPDATE (a 0 version would be read as a new row).
update item
set location_kind = 'GROUND',
    location_ref  = scene_id,
    version       = 1;

-- Now enforce NOT NULL (post-backfill) and drop the superseded column.
alter table item alter column location_kind set not null;
alter table item alter column location_ref  set not null;
alter table item alter column version       set not null;
alter table item drop column scene_id;
