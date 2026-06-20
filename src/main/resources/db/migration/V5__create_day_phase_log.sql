-- Day-phase-log schema: how far day-phase (dawn/dusk) announcements have progressed.
--
-- A world-singleton row. The background time ticker announces a day phase once per
-- occurrence; this column is the watermark that makes that idempotent and
-- restart-safe: the absolute game hour (game seconds since the epoch divided by the
-- calendar's seconds-per-hour) of the most recent phase announced, or -1 when none
-- has been announced yet. The log is seeded at game initialization (at -1) and
-- advanced when a new phase begins.
--
-- The schema is a deliberate design artifact (no ORM ddl-auto). The id is a fixed
-- singleton key owned by the persistence adapter, not a domain identity: the domain
-- DayPhaseLog carries only the watermark (there is exactly one log).

create table day_phase_log
(
    id varchar(64) primary key,

    -- Spring Data JDBC optimistic-locking version: 0 marks a new row, each write
    -- checks-and-increments it so a stale concurrent write is rejected. Carried all
    -- the way onto the domain DayPhaseLog (design-notes §5).
    version bigint not null,

    -- Absolute game hour announced through; -1 means nothing announced yet. The
    -- always-valid DayPhaseLog enforces the >= -1 floor in the domain.
    announced_through_hour bigint not null
);
