package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.GameDate;

/**
 * Presenter (driven) output port for {@code AskForTime}, co-located with its use case. Extends
 * {@link ClockReadinessPresenterOutputPort} (and thereby the catch-all {@code presentError}), inheriting the
 * shared {@code presentGameNotInitialized} readiness outcome and adding only this use case's success.
 *
 * <p>One success outcome. The date crosses as the positional {@link GameDate}, accompanied by the
 * {@link GameCalendar} it was placed on — the renderer needs the calendar to resolve the month and weekday
 * <em>names</em> and to label the indices, work that is rendering, not domain (a {@code GameDate} holds only
 * language-neutral integers). Both are immutable, so they pass straight through with no Response-Model DTO.
 */
public interface AskForTimePresenterOutputPort extends ClockReadinessPresenterOutputPort {

    /**
     * Happy path: the current game date, ready to be rendered for the player. {@code calendar} is supplied so
     * the renderer can resolve {@code date}'s month/weekday names and label its 0-based indices.
     */
    void presentCurrentTime(GameDate date, GameCalendar calendar);
}
