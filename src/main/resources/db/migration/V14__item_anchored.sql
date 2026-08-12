-- Anchored items (issue #90 follow-up): an item may be fixed where it stands, refused by `take`.
--
-- The domain stores the resolved fact inverted from the authored `portable:` key (false = carryable,
-- the safe default); the seed gate applies the kind-sensitive authoring defaults (a plain item is
-- portable, a container is anchored unless authored `portable: true`). Existing rows default to
-- carryable — a play database seeded before this migration keeps any already-spawned container
-- takeable; reset the play volume to pick up the authored fact.

alter table item
    add column anchored boolean not null default false;
