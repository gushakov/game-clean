-- NPC combat stance and attack cadence (#66 step 2).
--
-- Being struck by the player now turns an NPC HOSTILE, a persisted stance the animate
-- policy re-derives every tick to decide the counterattack (rather than a one-shot event).
-- `hostile` defaults false — spawned and existing NPCs start peaceful. `attack_chance` is
-- the authored per-tick odds a hostile, co-located NPC actually swings on a given round,
-- stored as its canonical num/den text exactly like `move_chance` (chance arithmetic never
-- happens in SQL, and the fraction reads directly in query results).

alter table npc add column hostile       boolean not null default false;
alter table npc add column attack_chance varchar(32);

-- Backfill any NPC already spawned into a local/dev world with a default attack cadence
-- (1/3 per 1s tick ~ a blow every ~3s once provoked); then enforce not-null + shape.
update npc set attack_chance = '1/3' where attack_chance is null;

alter table npc alter column attack_chance set not null;

-- The column gave up int typing for readability; this check keeps it honest (shape only —
-- the domain validity gate still owns the num <= den rule), mirroring npc_move_chance_format.
alter table npc add constraint npc_attack_chance_format check (attack_chance ~ '^\d+/\d+$');
