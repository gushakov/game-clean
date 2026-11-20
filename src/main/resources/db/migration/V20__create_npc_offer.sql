-- NPC offers (issue #117): the things an NPC stands ready to hand over for coins.
--
-- Owned children of the npc aggregate, exactly like the sayings of V16 — a child table with a
-- back-reference, mapped by Spring Data JDBC's @MappedCollection and loaded eagerly with the NPC.
-- The offers are copied from the authored template onto every spawned instance (there is no
-- persisted template), so two instances of one NPC each carry their own rows.
--
-- A List, not a Set: `idx` is the collection's key column, so an NPC's catalogue reads in the
-- order it was authored.
--
-- `ref` is the offer's AUTHORED HANDLE within its NPC and `item_ref` the handle of the `items:`
-- entry handed over on purchase. Neither is a foreign key — `item_ref` names an authoring handle,
-- not a persisted item instance (nothing exists until a later phase mints the delivered item), and
-- whether it resolves to a sellable item is the seed gate's rule, decided before any NPC is spawned.
-- The domain's "no two offers of one NPC share a handle" is enforced structurally as well as at the
-- construction gate, by the unique constraint below.
--
-- `price` counts COINS — one unit of the single currency — and is a plain int: no denominations, no
-- Price value object, nothing in this phase reads it. Its lower bound is the domain gate's.
--
-- Existing rows (worlds seeded before this migration) simply have no offers — a valid state (the
-- NPC sells nothing) — so no backfill.

create table npc_offer
(
    -- Ownership back-reference: which NPC makes this offer. FK-enforced — an offer cannot outlive
    -- its NPC (Spring Data JDBC deletes children before the root on a slaying's delete, and
    -- rewrites them on every save of the aggregate).
    npc_id   varchar(64)  not null references npc (id),

    -- 0-based position in the authored list (the @MappedCollection key column).
    idx      int          not null,

    -- The offer's authored handle, unique within the owning NPC (see the unique constraint below).
    ref      varchar(64)  not null,

    -- How the offer reads to the player — a sentence, authored prose like an item's description.
    name     varchar(255) not null,

    -- What it costs, counted in coins. At least one — the domain gate's rule, not the schema's.
    price    int          not null,

    -- The authoring handle of the item handed over on purchase. Not a foreign key: an authored
    -- handle, the npc.corpse_ref precedent.
    item_ref varchar(64)  not null,

    primary key (npc_id, idx),

    -- "An NPC's offer handles are unique" — the construction gate's rule, held structurally too.
    constraint npc_offer_ref_unique unique (npc_id, ref)
);
