package com.github.gameclean.core.usecase.npc;

/**
 * Driving (input) port for the <b>AnimateNpcs</b> system goal: advance the world's NPCs one tick of autonomous
 * activity. A driving adapter — the background NPC-activity ticker — invokes it; the initiating actor is the
 * <em>system</em>, not the player. It is the NPC peer of {@code AnnounceTimeOfDay}'s system-actor observation.
 *
 * <p>One interaction, {@link #systemAdvancesNpcs()}, named as its Cockburn step — subject (the system) and
 * predicate (advances the NPCs). One interaction handles <em>all</em> NPCs per tick (use cases are {@code void},
 * so enumerate-and-decide is a single interaction converging on one presentation), rather than a per-NPC
 * interaction. It is a <b>read-only policy</b> (issue #66 step 2): it decides each NPC's action from persisted
 * state and <em>dispatches</em> it as a command for an executing interaction to carry out — it writes nothing
 * itself.
 *
 * <p>It takes <b>no parameter</b> and is {@code void}: there is nothing to supply (the NPCs, the scenes, and the
 * player's location are all resolved inside the use case), and its own outcome — always the quiet stripe, since
 * the dispatched executions narrate themselves — is reported through the presenter, never returned.
 */
public interface AnimateNpcsInputPort {

    /**
     * Advances the NPCs one tick: enumerates every NPC, derives each one's decision from its persisted stance
     * (a hostile, co-located NPC may strike; a non-hostile NPC may wander), and dispatches each decided action as
     * a command. Writes nothing and always presents the quiet outcome — the executions narrate themselves.
     */
    void systemAdvancesNpcs();
}
