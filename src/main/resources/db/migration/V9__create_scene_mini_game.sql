-- Mini-games offered per scene (issue #72).
--
-- An authored scene attribute (the seed's `mini-games:` key): which mini-games
-- can be played here. Owned children of the scene aggregate, exactly like exits —
-- a child table with a back-reference, mapped by Spring Data JDBC's
-- @MappedCollection and loaded eagerly with the scene. The game itself (a
-- blackjack round) is deliberately NOT persisted anywhere: a round is an
-- ephemeral value living in the terminal session; this table only records what
-- is on offer.

create table scene_mini_game
(
    -- Ownership back-reference: which scene offers this game. FK-enforced — the
    -- offering cannot outlive its scene.
    scene_id  varchar(64) not null references scene (id),

    -- The mini-game's name from the closed domain vocabulary (MiniGame enum),
    -- stored as the enum constant, e.g. 'BLACKJACK'. The composite PK makes
    -- "a scene offers a game at most once" structural.
    mini_game varchar(32) not null,

    primary key (scene_id, mini_game)
);
