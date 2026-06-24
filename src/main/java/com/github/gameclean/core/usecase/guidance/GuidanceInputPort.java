package com.github.gameclean.core.usecase.guidance;

/**
 * Driving (input) port for the <b>Guidance</b> user goal: the player is steered toward what they can do. A
 * driving adapter — the interactive console, when the {@link com.github.gameclean.infrastructure.terminal.command
 * command parser} maps a line to no known intent — invokes it to respond to a lost player.
 *
 * <p>One interaction, {@link #playerIssuesUnrecognizedCommand(String)}, whose initiating actor is the
 * <em>player</em>. Its name is the Cockburn step it implements — subject (the player) and predicate (issues an
 * unrecognized command) — naming the entry condition that triggers the guidance goal, not a bare service verb.
 * It takes the raw input only so the outcome can echo it back; the use case never interprets that string —
 * recognizing it as unknown already happened in the parser (design-notes §9), and the concrete command list
 * lives in the presenter, never here.
 *
 * <p>It is {@code void}: the outcome (a guidance prompt, or an unexpected error) is reported through the
 * {@link GuidancePresenterOutputPort}, never returned. A presenter-only goal — it reads no domain state and
 * touches no transaction.
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
}
