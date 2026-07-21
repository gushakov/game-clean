package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.daytime.DayPhase;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code AnnounceTimeOfDay}, co-located with its use case. It declares
 * exactly the outcomes this use case presents: its two own outcomes, the clock-readiness precondition it
 * checks inline, and the inherited catch-all — the same per-port declaration as {@code AskForTime} and
 * {@code SuspendGame}, with only the rendering shared in the adapter.
 *
 * <p>Two outcomes of its own, both deliberately present (so "exactly one {@code present*} per run" holds even
 * for a quiet poll — the use case never silently returns without presenting):
 * <ul>
 *   <li>{@link #presentDayPhaseBegan(DayPhase, String)} — a new day phase has begun; the chosen flavour line
 *       is announced. This is the project's first <em>asynchronous</em> presentation: a system actor producing
 *       output the player sees mid-session (rendered with JLine {@code printAbove}).</li>
 *   <li>{@link #presentNothingToAnnounce()} — the common case: the observation crossed no new day phase, so
 *       there is nothing to say. A real outcome the adapter renders as silence (a trace log), not a missing
 *       presentation.</li>
 * </ul>
 *
 * <p>The chosen message crosses as a plain {@link String} (the phase already selected it from its lines), and
 * the {@link DayPhase} accompanies it so the renderer can label the announcement with the phase name. Neither
 * the date nor the calendar is passed: the time-of-day boundary is the occasion, not the content.
 */
public interface AnnounceTimeOfDayPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Precondition outcome: the game clock has not been initialized, so the world is not yet in a playable
     * state. Reached by branch-and-present (then {@code return}) when the clock is absent — not by throwing.
     */
    void presentGameNotInitialized();

    /**
     * A new day phase has begun: announce the selected flavour line to the player.
     *
     * @param phase   the day phase that has just begun (its name labels the announcement)
     * @param message the line chosen from the phase's authored messages
     */
    void presentDayPhaseBegan(DayPhase phase, String message);

    /**
     * Quiet observation: no new day phase began (or it was already announced), so there is nothing to say.
     */
    void presentNothingToAnnounce();
}
