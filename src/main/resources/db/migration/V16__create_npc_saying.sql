-- NPC sayings (issue #104): the authored lines an NPC answers with when the player
-- talks to it.
--
-- Owned children of the npc aggregate, exactly like a scene's exits and mini-games —
-- a child table with a back-reference, mapped by Spring Data JDBC's @MappedCollection
-- and loaded eagerly with the NPC. The lines are copied from the authored template
-- onto every spawned instance (there is no persisted template), so two instances of
-- one template each carry their own rows.
--
-- A List, not a Set: `idx` is the collection's key column, so authored order survives
-- the round-trip and an NPC may repeat a line verbatim if its author wants it to.
--
-- Existing rows (worlds seeded before this migration) simply have no sayings — a
-- valid state (the NPC has nothing to say) — so no backfill.

create table npc_saying
(
    -- Ownership back-reference: which NPC says this. FK-enforced — a saying cannot
    -- outlive its NPC (Spring Data JDBC deletes children before the root on a
    -- slaying's delete, and rewrites them on every save of the aggregate).
    npc_id varchar(64) not null references npc (id),

    -- 0-based position in the authored list (the @MappedCollection key column).
    idx    int         not null,

    -- The line itself, authored prose.
    text   text        not null,

    primary key (npc_id, idx)
);
