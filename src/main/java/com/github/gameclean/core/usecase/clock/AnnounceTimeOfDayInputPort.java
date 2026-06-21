package com.github.gameclean.core.usecase.clock;

/**
 * Driving (input) port for the <b>AnnounceTimeOfDay</b> system goal: as game time advances, the world
 * announces the dawn/dusk lines that mark a new day phase. A driving adapter — the background time ticker —
 * invokes it; the initiating actor is the <em>system</em> (the clock), not the player. It is the time-system
 * peer of {@code InitializeGame}'s system-actor seeding and of the player-actor console commands.
 *
 * <p>One interaction, {@link #systemObservesTimeOfDay()}, named as its Cockburn step — subject (the system)
 * and predicate (observes the time of day) — not a bare service verb. "Observes" is the step; <em>announcing</em>
 * is one possible outcome, because the ticker is a blind metronome that fires on a fixed real-time interval:
 * most observations cross no day-phase boundary and announce nothing.
 *
 * <p>It takes <b>no parameter</b> and is {@code void}: there is nothing to supply (the current time is derived
 * inside the use case from the persisted clock and the live session's elapsed seconds), and every outcome —
 * a day phase began, nothing to announce, the game not yet initialized, or an unexpected error — is reported
 * through the presenter, never returned.
 */
public interface AnnounceTimeOfDayInputPort {

    /**
     * Observes the current time of day and, if game time has just entered an authored day phase not yet
     * announced, announces it. Derives "now" inside the use case from the persisted
     * {@link com.github.gameclean.core.model.clock.GameClock} and the session-elapsed seconds, places it on
     * the authored calendar, and dedups against the persisted
     * {@link com.github.gameclean.core.model.daytime.DayPhaseLog} watermark.
     */
    void systemObservesTimeOfDay();
}
