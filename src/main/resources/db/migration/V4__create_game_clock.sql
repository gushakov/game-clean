-- Game clock schema: the world's banked game time (Model B accumulated play-time).
--
-- A world-singleton row. Game time advances only while the game is played; this
-- column holds the total game seconds banked across all sessions so far. Current
-- time is this total plus the live session's elapsed seconds (derived at runtime,
-- never stored). The clock is seeded at game initialization (at zero) and updated
-- when the player leaves (suspend banks the session's seconds into it).
--
-- The schema is a deliberate design artifact (no ORM ddl-auto). The id is a fixed
-- singleton key owned by the persistence adapter, not a domain identity: the domain
-- GameClock carries only the banked seconds (there is exactly one clock).

create table game_clock
(
    id varchar(64) primary key,

    -- Total game seconds banked across sessions; never negative (the always-valid
    -- GameClock enforces this in the domain).
    accumulated_game_seconds bigint not null
);
