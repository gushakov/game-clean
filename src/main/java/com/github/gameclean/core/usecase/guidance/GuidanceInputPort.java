package com.github.gameclean.core.usecase.guidance;

/**
 * Driving (input) port for the <b>Guidance</b> user goal: orient the player toward what they can do. Two
 * driving triggers, both from the interactive console — one when the player types something the
 * {@link com.github.gameclean.infrastructure.terminal.command command parser} maps to no known intent, and one
 * when the session opens and the system greets the player.
 *
 * <p>Two interactions with <em>different initiating actors</em>, grouped because they advance one coherent goal
 * — telling the player how to interact — to one audience (the player console). A use case may host more than
 * one actor: the cohesion unit is the goal, not the actor (clean-ddd-core §0).
 *
 * <ul>
 *   <li>{@link #playerIssuesUnrecognizedCommand(String)} — actor the <em>player</em>: they typed something
 *       unrecognized. The raw input crosses inward only so the outcome can echo it; the use case never
 *       interprets it (recognizing it as unknown already happened in the parser, design-notes §9).</li>
 *   <li>{@link #systemGreetsPlayer()} — actor the <em>system</em> at session start: an input-less greeting,
 *       fired as the console's first turn before any line is read (design-notes §9, the loop as the
 *       internalized request-dispatcher).</li>
 * </ul>
 *
 * <p>Both names are the Cockburn step — subject (player / system) and predicate — not a bare service verb.
 * Both are {@code void}: outcomes are reported through the {@link GuidancePresenterOutputPort}, never returned.
 * A presenter-only goal — it reads no domain state and touches no transaction. The concrete command list both
 * outcomes show lives in the presenter, never here.
 */
public interface GuidanceInputPort {

    /**
     * Guides the player after they typed something the delivery mechanism could not recognize. The raw input
     * crosses inward as a primitive purely so the presented guidance can echo it; the use case does not parse
     * or interpret it.
     *
     * @param input the raw line the player typed that matched no known command
     */
    void playerIssuesUnrecognizedCommand(String input);

    /**
     * Greets the player as the session opens — the system's own first "turn", presenting the welcome and the
     * available commands. Takes no parameter and reads no state: the greeting is fixed (it will gain inputs
     * only if it ever grows domain-aware, e.g. greeting by time of day).
     */
    void systemGreetsPlayer();
}
