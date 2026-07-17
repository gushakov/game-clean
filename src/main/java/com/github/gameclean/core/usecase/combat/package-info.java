/**
 * The <b>combat</b> summary goal (Cockburn): interactions where the player and NPCs come to blows. Its first
 * user goal is {@code Hit} ({@link com.github.gameclean.core.usecase.combat.HitInputPort}) — the player
 * strikes an NPC in their current scene — the project's <em>first player↔NPC interaction</em> and first
 * <em>contested</em> aggregate with two independent writer kinds (this goal and the wandering ticker).
 *
 * <p><b>The strike resolves synchronously, in the player's own interaction.</b> {@code Hit} mirrors
 * {@code take}'s two-interaction designate-by-description / designate-by-choice shape over the same
 * {@code orient}+{@code select} opening (here binding {@code select} to an <em>NPC-in-scene</em> candidate,
 * its first non-item consumer) and <em>writes</em>: it rolls the damage, lowers the NPC's hit points, and
 * persists in one narrow transaction. Its outcome stripes — struck / slain / target got away — are what the
 * player needs to choose their next action, so they are presented here and now, not dispatched as an event to
 * resolve later (which would cut the user goal in the middle). Step 1 does a flat {@code rollDie(10)} of
 * damage; no to-hit roll (every swing lands), no weapon or stat block — those wait for the interaction that
 * demands them.
 *
 * <p><b>Retaliation is deferred (issue #66 step 2).</b> An NPC striking back is modelled as a persisted
 * <em>stance</em> derived by the animate ticker, not a one-shot event — so it lives with {@code npc}, not
 * here. This package is the synchronous strike only.
 */
package com.github.gameclean.core.usecase.combat;
