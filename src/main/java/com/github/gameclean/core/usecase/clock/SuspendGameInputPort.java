package com.github.gameclean.core.usecase.clock;

/**
 * Driving (input) port for the <b>SuspendGame</b> user goal: the player leaves the game, and the time they
 * spent this session is banked so the world clock resumes from there next time. A driving adapter — the
 * interactive console's {@code bye}/{@code quit} command — invokes it as the player exits.
 *
 * <p>One interaction, {@link #playerLeavesTheGame()}, whose initiating actor is the <em>player</em>. Its name
 * is the Cockburn step — subject (the player) and predicate (leaves the game). It takes <b>no parameter</b>:
 * how long the session ran is pulled from the time source inside the use case.
 *
 * <p>This is the Model B "pause on quit" write: under Model B, game time advances only while playing, so the
 * live session's elapsed seconds are folded into the persisted clock on the way out. For now the banking
 * happens only here, on an explicit {@code bye} (no periodic ticker) — a hard kill loses the current
 * session's unbanked seconds, accepted until an autosave/ticker arrives.
 *
 * <p>It is {@code void}: its success (the time is banked) and any failure are reported through the
 * {@link SuspendGamePresenterOutputPort}, never returned.
 */
public interface SuspendGameInputPort {

    /**
     * Banks the live session's elapsed game seconds into the persisted clock — the player "leaves the game".
     * The session-elapsed figure is resolved inside the use case from the time source.
     */
    void playerLeavesTheGame();
}
