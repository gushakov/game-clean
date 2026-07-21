-- Collapse the NPC move chance from a (move_chance_num, move_chance_den) int pair
-- into a single varchar column holding the fraction's canonical num/den text form
-- (e.g. '1/40') — the same rendering the authored world files use.
--
-- Chance arithmetic never happens in SQL, so the pair bought nothing over the text
-- form, which reads directly in query results; and one scalar column is what lets the
-- domain Chance value object convert through the shared MapStruct scalar-converter
-- regime (like the id wrappers) instead of a per-mapper two-column reconstitution.
-- Fix-forward migration: V7 (which created the pair) is merged history and stays
-- untouched.

alter table npc add column move_chance varchar(32);

-- Backfill any NPC already spawned into a local/dev world from the existing pair.
update npc
set move_chance = move_chance_num || '/' || move_chance_den;

alter table npc alter column move_chance set not null;

-- The column gave up its int typing for readability; this check keeps it honest
-- (shape only — the domain validity gate still owns the num <= den rule).
alter table npc add constraint npc_move_chance_format check (move_chance ~ '^\d+/\d+$');

alter table npc drop column move_chance_num;
alter table npc drop column move_chance_den;
