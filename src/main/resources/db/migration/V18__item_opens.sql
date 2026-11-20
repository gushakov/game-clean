-- Keys (issue #115): an item may open a lock on an exit.
--
-- `opens` is the AUTHORED HANDLE of the lock the item opens (an `items:` entry's `opens:` key in
-- the seed), copied onto every spawned instance — like `npc.corpse_ref`, an opaque handle, not a
-- foreign key: the seed gate checks that it names a lock declared on some authored exit.
--
-- Nullable on purpose: an item that opens nothing stays null. Existing rows stay null; no backfill.

alter table item
    add column opens varchar(64);
