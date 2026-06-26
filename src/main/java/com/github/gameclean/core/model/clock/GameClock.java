package com.github.gameclean.core.model.clock;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * The world's accumulated game time — the single anchor from which the current {@code GameDate} is derived.
 * A world-singleton aggregate holding one value, {@link #accumulatedGameSeconds}: the total game seconds
 * banked across all play sessions so far. Equality is by value (there is exactly one clock; its identity is
 * not meaningful).
 *
 * <p><strong>Model B — accumulated play-time.</strong> Game time advances only <em>while the game is being
 * played</em>: closing the game pauses time rather than letting it accrue against the wall clock. So the
 * current elapsed game time is this banked total <em>plus</em> however many seconds the live session has been
 * running — {@link #elapsedWith(long)} — and at the end of a session that session's seconds are folded back in
 * — {@link #accumulate(long)} — to become the new banked total. The session-elapsed figure itself is wall-clock
 * derived (one real second is one game second) and supplied by a time-source port; this aggregate owns only
 * the banking arithmetic, not where "how long has this session run" comes from.
 *
 * <p>Always-valid: the banked total can never be negative. The clock only ever moves forward, so
 * {@link #elapsedWith}/{@link #accumulate} reject a negative session-elapsed as a caller bug (a plain
 * {@code IllegalArgumentException}, the behaviour-method guard convention — distinct from the construction
 * gate's {@link InvalidDomainObjectError}).
 */
@Getter
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GameClock {

    long accumulatedGameSeconds;

    @Builder
    public GameClock(long accumulatedGameSeconds) {
        if (accumulatedGameSeconds < 0) {
            throw new InvalidDomainObjectError(
                    "accumulated game seconds must not be negative, got " + accumulatedGameSeconds);
        }
        this.accumulatedGameSeconds = accumulatedGameSeconds;
    }

    /**
     * The clock of a fresh world: zero seconds banked. Seeded once at game initialization.
     *
     * @return a clock at the start of game time
     */
    public static GameClock initial() {
        return new GameClock(0);
    }

    /**
     * The current elapsed game seconds: the banked total plus the live session's elapsed seconds. The figure
     * fed to the calendar to place "now". A read — it does not change the banked total.
     *
     * @param sessionElapsedSeconds game seconds elapsed in the live session so far (must not be negative)
     * @return the total elapsed game seconds as of now
     */
    public long elapsedWith(long sessionElapsedSeconds) {
        if (sessionElapsedSeconds < 0) {
            throw new IllegalArgumentException(
                    "session elapsed seconds must not be negative, got " + sessionElapsedSeconds);
        }
        return accumulatedGameSeconds + sessionElapsedSeconds;
    }

    /**
     * Banks the live session's elapsed seconds into a new clock — the Model B "pause on quit" step, run when
     * the player leaves. The aggregate is immutable, so this yields a <em>new</em> clock with the higher
     * total; the original is untouched.
     *
     * @param sessionElapsedSeconds game seconds elapsed in the session being ended (must not be negative)
     * @return a new clock whose banked total includes this session
     */
    public GameClock accumulate(long sessionElapsedSeconds) {
        return new GameClock(elapsedWith(sessionElapsedSeconds));
    }
}
