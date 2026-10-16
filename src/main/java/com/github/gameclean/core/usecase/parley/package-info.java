/**
 * The <b>parley</b> summary goal (Cockburn): interactions where the player <em>talks</em> with the world's
 * inhabitants rather than fighting them. Its first user goal is {@code TalkToNpc}
 * ({@link com.github.gameclean.core.usecase.parley.TalkToNpcInputPort}) — the first non-violent NPC
 * interaction, and the third consumer of the NPC-in-scene {@code select} subcase after {@code hit}.
 *
 * <p><b>Why its own goal, not an arm of {@code combat} or {@code npc}.</b> {@code combat} is "the player and
 * NPCs come to blows"; {@code npc} is the <em>system-actor</em> goal (the animate policy and the executing
 * {@code Wander}). Addressing an NPC is a different intent of the player's — the same designation opening,
 * a different purpose — so it gets the package its name screams (#104).
 *
 * <p><b>Read-only, like {@code look}.</b> Talking changes nothing in the world: no transaction, no write, no
 * persistence port of the use case's own (the select subcase holds the NPC port). The NPC answers with one of
 * its authored sayings, picked by a {@code Dice} the use case holds as a domain collaborator — the entropy of a
 * reply, like the entropy of a spawn or a day-phase announcement, is in-model.
 *
 * <p><b>Three stripes, decided here on domain facts.</b> A <em>hostile</em> NPC (one the player has struck)
 * refuses to talk — the combat stance shuts the conversation, an interaction-shape decision the use case makes
 * by reading {@code Npc.isHostile()}, never the presenter; a peaceful NPC with no authored sayings has nothing
 * to say ({@code Npc.hasSayings()}); otherwise it speaks. The NPC owns the pick; the use case owns the
 * branching; the presenter only phrases.
 */
package com.github.gameclean.core.usecase.parley;
