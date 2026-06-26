package com.github.gameclean.core.port.clock;

/**
 * Driven (output) port supplying how long the live play session has been running, in game seconds. The
 * {@code OutputPort} suffix marks the hexagonal direction: the core asks; an infrastructure adapter backs it
 * with the real wall clock.
 *
 * <p>Under the Model B clock, current game time is the banked total plus this session-elapsed figure (see
 * {@link com.github.gameclean.core.model.clock.GameClock}). One real second is one game second, so this is
 * the real seconds since the session began. Reading the wall clock is non-deterministic infrastructure, so it
 * crosses through this port rather than a static {@code Instant.now()} — stubbing it with a fixed figure
 * keeps the time-reading use cases deterministic under test. Note the deliberate asymmetry with game dice
 * ({@link com.github.gameclean.core.model.dice.Dice}, a <em>domain</em> capability, not a port): wall-clock
 * time is the world <em>outside</em> the game, delivered as a value the shell reads once, so it stays an
 * output port; dice are <em>part</em> of the game, so the model owns them.
 *
 * <p>This is a different role from the persisted {@link com.github.gameclean.core.model.clock.GameClock}: this
 * port measures the <em>current session</em>; the clock holds the <em>banked total</em> across sessions.
 */
public interface GameTimeSourceOutputPort {

    /**
     * @return game seconds elapsed since the current session started — never negative.
     */
    long elapsedSessionSeconds();
}
