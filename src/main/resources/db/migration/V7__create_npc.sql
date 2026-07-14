-- NPC schema: non-player characters spawned into the world.
--
-- An NPC is an aggregate root with its own runtime-generated id (the domain NpcId
-- value object, prefix 'npc' + a generated body), the twin of the item table. Like
-- items, NPC ids are not authored — one authored template spawns several instances,
-- each minted a fresh id. The schema is a deliberate design artifact (no ORM ddl-auto).
--
-- No optimistic-locking version column (unlike item, V6): the autonomous-movement
-- ticker is the NPC's only writer today (single-writer aggregate), so there is no
-- take-vs-take race to arbitrate. A version arrives only when the player can affect
-- an NPC — the trigger for a second writer.

create table npc
(
    id                varchar(64)  primary key,

    -- The scene this NPC currently stands in, referenced by identity. Deliberately
    -- NOT a foreign key (mirroring exit.target_scene_id and player.current_scene_id):
    -- an NPC references where it is, and whether that id resolves to a known scene is
    -- an inter-aggregate concern, not a database constraint.
    current_scene_id  varchar(64)  not null,

    short_description varchar(255) not null,
    full_description  text         not null,

    -- The authored odds (numerator/denominator) that this NPC wanders on a given tick.
    move_chance_num   int          not null,
    move_chance_den   int          not null
);
