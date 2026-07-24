package com.github.gameclean.core.usecase.npc;

/**
 * Driving (input) port for the <b>Wander</b> system goal: one NPC steps through a named exit into the adjacent
 * scene. The initiating actor is the <em>NPC</em> (a secondary/system actor), but the interaction is not typed
 * by anyone — the animate policy decides the wander (which NPC, which exit) and dispatches it as a command,
 * which a driving adapter (the NPC command session) turns into this call. The wander twin of
 * {@code FightNpc.npcStrikesPlayer}: the two executing interactions the policy dispatches.
 *
 * <p>One interaction, {@link #npcWandersThrough(String, String)}. The exit was already chosen by the policy
 * (which owns the dice), so this interaction does not re-roll; it only <b>executes</b> the move — re-validating
 * at execution time (the NPC may be gone/dead, its scene or the named exit gone) and persisting the new
 * position. It is {@code void}: a witnessed movement is narrated and every miss is a quiet outcome, through the
 * presenter, never returned. Primitive ids cross the boundary; the use case constructs the {@code NpcId}.
 */
public interface WanderInputPort {

    /**
     * Moves the given NPC through the named exit of its current scene into the adjacent scene, then narrates the
     * movement if the player can witness it (a departure from, or an arrival into, the player's current scene).
     * Re-validates at execution: a gone-or-dead NPC, an unresolvable current scene, a vanished exit, or a
     * dangling target is a quiet no-op (the loop re-derives next tick), as is a lost optimistic-lock race.
     *
     * @param npcId    the wandering NPC's id, as a primitive carrier — the use case constructs the {@code NpcId}
     * @param exitName the name of the exit the policy chose for it to leave by
     */
    void npcWandersThrough(String npcId, String exitName);
}
