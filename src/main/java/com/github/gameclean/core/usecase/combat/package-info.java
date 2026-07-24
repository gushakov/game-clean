/**
 * The <b>combat</b> summary goal (Cockburn): interactions where the player and NPCs come to blows. Its user
 * goal is {@code FightNpc} ({@link com.github.gameclean.core.usecase.combat.FightNpcInputPort}) — the
 * project's <em>multi-actor</em> use case and first <em>contested</em> aggregate with two independent writer
 * kinds (this goal and the animate policy's executions).
 *
 * <p><b>Two actors, one use case.</b> {@code FightNpc} holds both sides of the fight. The <em>player</em> is
 * the primary actor: they initiate combat by striking an NPC ({@code playerHitsTarget} /
 * {@code playerHitsChosenCandidate}, the same {@code take}-shaped designate-by-description / designate-by-choice
 * pair over the {@code orient}+{@code select} opening, here binding {@code select} to an NPC-in-scene
 * candidate). Once struck, an NPC becomes a hostile <em>secondary actor</em> that initiates its own step
 * ({@code npcStrikesPlayer}) — the multi-actor Cockburn shape this showcase wanted a live example of. The
 * orthodox reading that a secondary actor is only one the <em>system enlists</em> is acknowledged and answered
 * by the methodology's own table, which lists secondary actors <em>initiating steps</em>.
 *
 * <p><b>The strike resolves synchronously, in the acting actor's own interaction.</b> Each blow rolls a flat
 * {@code rollDie(10)} of damage, lowers the target's hit points, and persists in one narrow transaction; a
 * player's blow also provokes a surviving NPC into the hostile stance. Its outcome stripes — struck / slain /
 * got away, and on the NPC side struck-player / player-slain / a quiet whiff — are what the actor needs to
 * situate its next action, so they are presented here and now.
 *
 * <p><b>Decision polled, execution dispatched (issue #66 step 2).</b> The player's mind decides outside the
 * hexagon and speaks through the console; the NPC's mind is the animate <em>policy</em> (a read-only tick),
 * which derives the counterattack from persisted hostility + co-location + an authored attack gate and
 * <em>dispatches</em> it as a command. A driving adapter turns that command back into {@code npcStrikesPlayer}
 * here — a use case never calls another use case; one actor's decided action reaches another interaction only
 * out through a driven port and back in through a driving adapter. Combat is thus a persisted <em>stance</em>
 * derived by a loop, not a one-shot event: the tick cadence gives combat its rounds for free.
 */
package com.github.gameclean.core.usecase.combat;
