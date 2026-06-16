-- Player schema: the single player and where it currently stands.
--
-- The player is an aggregate root with minimal state for now: just its current
-- position. The schema is a deliberate design artifact (no ORM ddl-auto): the id
-- is the authored short key carried by the domain PlayerId value object (e.g.
-- 'plr1').

create table player
(
    id varchar(64) primary key,

    -- The scene the player currently occupies, referenced by identity. Deliberately
    -- NOT a foreign key (mirroring exit.target_scene_id): whether the id resolves to a
    -- known scene is an inter-aggregate rule, surfaced by the Look use case as a domain
    -- outcome rather than enforced as an FK violation.
    current_scene_id varchar(64) not null
);
