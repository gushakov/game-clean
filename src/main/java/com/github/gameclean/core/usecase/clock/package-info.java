/**
 * Summary goal: <b>keep and consult game time</b>. The interactions here let the player read the world clock
 * and bank their play time across sessions, on top of the always-valid calendar model
 * ({@link com.github.gameclean.core.model.calendar.GameCalendar}) and the persisted
 * {@link com.github.gameclean.core.model.clock.GameClock}.
 *
 * <p>Three user/system goals so far. {@link com.github.gameclean.core.usecase.clock.AskForTimeInputPort
 * AskForTime}: the <em>player</em> checks the current date — a read-only interaction that derives "now" from
 * the banked clock plus the live session's elapsed seconds and places it on the calendar. {@link
 * com.github.gameclean.core.usecase.clock.SuspendGameInputPort SuspendGame}: the player leaves, and the
 * session's elapsed time is banked into the clock (the Model B "pause on quit" write). {@link
 * com.github.gameclean.core.usecase.clock.AnnounceTimeOfDayInputPort AnnounceTimeOfDay}: the <em>system</em>
 * (a background ticker) observes the time of day and announces the dawn/dusk lines marking a new day phase,
 * dedup'd against a persisted watermark.
 *
 * <p><b>Model B — accumulated play-time.</b> Game time advances only while the game is being played; closing
 * it pauses time rather than letting it accrue against the wall clock. Current time is therefore the banked
 * total plus the live session, and banking happens on an explicit {@code bye}. A background ticker now
 * <em>observes</em> game time (to announce day phases) but never advances or banks it — the accumulator still
 * only moves on {@code bye}. The session-elapsed figure is wall-clock derived behind {@link
 * com.github.gameclean.core.port.clock.GameTimeSourceOutputPort}, kept off the model so the use cases stay
 * deterministic under test.
 */
package com.github.gameclean.core.usecase.clock;
