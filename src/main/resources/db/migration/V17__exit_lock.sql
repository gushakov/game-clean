-- Locked exits (issue #115): an exit may carry a lock, passable only by a mover carrying an
-- item that opens it.
--
-- `lock_ref` is the AUTHORED HANDLE of the lock (the exit's `lock:` key in the seed) — the
-- value an item's `opens` names. It is not an identity and carries no foreign key: whether some
-- item opens it is decided at move time against what the mover carries.
-- `locked_description` is the optional line shown when a move is refused.
--
-- The lock holds no state: there is no locked/unlocked column, and a scene is never written
-- after seeding. Both columns are nullable — null `lock_ref` is an ordinary exit. Existing rows
-- (worlds seeded before this migration) stay null; no backfill.

alter table exit
    add column lock_ref varchar(64),
    add column locked_description text;
