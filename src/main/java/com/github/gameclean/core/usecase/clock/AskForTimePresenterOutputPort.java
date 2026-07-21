package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.GameDate;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code AskForTime}, co-located with its use case. It declares exactly
 * the outcomes this use case presents: its success, the clock-readiness precondition it checks inline, and
 * the inherited catch-all. {@code SuspendGame} and {@code AnnounceTimeOfDay} check the same precondition and
 * declare the same outcome on their <em>own</em> ports — a deliberate per-port declaration (the
 * {@code inventory} precedent), not a shared readiness super-port: only the rendering is shared, in the
 * adapter.
 *
 * <p>One success outcome. The date crosses as the positional {@link GameDate}, accompanied by the
 * {@link GameCalendar} it was placed on — the renderer needs the calendar to resolve the month and weekday
 * <em>names</em> and to label the indices, work that is rendering, not domain (a {@code GameDate} holds only
 * language-neutral integers). Both are immutable, so they pass straight through with no Response-Model DTO.
 */
public interface AskForTimePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Precondition outcome: the game clock has not been initialized, so the world is not yet in a playable
     * state. Reached by branch-and-present (then {@code return}) when the clock is absent — not by throwing.
     */
    void presentGameNotInitialized();

    /**
     * Happy path: the current game date, ready to be rendered for the player. {@code calendar} is supplied so
     * the renderer can resolve {@code date}'s month/weekday names and label its 0-based indices.
     */
    void presentCurrentTime(GameDate date, GameCalendar calendar);
}
