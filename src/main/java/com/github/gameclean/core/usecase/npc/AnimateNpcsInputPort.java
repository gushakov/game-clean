package com.github.gameclean.core.usecase.npc;

/**
 * Driving (input) port for the <b>AnimateNpcs</b> system goal: advance the world's NPCs one tick of autonomous
 * activity. A driving adapter — the background NPC-activity ticker — invokes it; the initiating actor is the
 * <em>system</em>, not the player. It is the NPC peer of {@code AnnounceTimeOfDay}'s system-actor observation.
 *
 * <p>One interaction, {@link #systemAdvancesNpcs()}, named as its Cockburn step — subject (the system) and
 * predicate (advances the NPCs). One interaction handles <em>all</em> NPCs per tick (use cases are {@code void},
 * so enumerate-and-act is a single interaction converging on one presentation), rather than a per-NPC move
 * interaction. "Advances" is the step; a wanderer moving — and, if the player can see it, a narrated
 * departure/arrival — is one possible outcome, because the ticker is a blind metronome and each NPC's move is a
 * dice roll: most ticks may move no one, or move NPCs the player cannot see.
 *
 * <p>It takes <b>no parameter</b> and is {@code void}: there is nothing to supply (the NPCs, the scenes, and the
 * player's location are all resolved inside the use case), and every outcome — some perceptible movements, or
 * nothing the player can witness — is reported through the presenter, never returned.
 */
public interface AnimateNpcsInputPort {

    /**
     * Advances the NPCs one tick: enumerates every NPC, rolls each one's move chance, and on a hit wanders it to
     * a random adjacent scene, persisting the moves and narrating any the player can witness. A tick with no
     * NPCs, no movement, or no <em>perceptible</em> movement presents the quiet outcome.
     */
    void systemAdvancesNpcs();
}
