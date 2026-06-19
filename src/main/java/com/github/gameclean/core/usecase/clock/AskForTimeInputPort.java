package com.github.gameclean.core.usecase.clock;

/**
 * Driving (input) port for the <b>AskForTime</b> user goal: the player asks what the date and time are in
 * the game world. A driving adapter — the interactive console's {@code now}/{@code time} command — invokes
 * it to report the current game date.
 *
 * <p>One interaction, {@link #playerChecksTheTime()}, whose initiating actor is the <em>player</em>. Its
 * name is the Cockburn step it implements — subject (the player) and predicate (checks the time) — not a
 * bare service verb. It takes <b>no parameter</b>: there is nothing to supply; the current time is derived
 * from the persisted clock and the live session's elapsed seconds, both pulled inside the use case.
 *
 * <p>It is {@code void}: every outcome (the current date, or an unexpected error) is reported through the
 * {@link AskForTimePresenterOutputPort}, never returned. A read-only interaction — it touches no transaction.
 */
public interface AskForTimeInputPort {

    /**
     * Reports the current game date — the player "checks the time". Derives it inside the use case from the
     * persisted {@link com.github.gameclean.core.model.clock.GameClock} and the session-elapsed seconds, and
     * places it on the authored calendar.
     */
    void playerChecksTheTime();
}
