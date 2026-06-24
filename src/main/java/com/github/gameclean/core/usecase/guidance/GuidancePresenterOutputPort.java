package com.github.gameclean.core.usecase.guidance;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Guidance}, co-located with its use case. Extends the catch-all
 * {@link ErrorHandlingPresenterOutputPort#presentError(Exception)} and adds this goal's outcomes.
 *
 * <p>Both outcomes are deliberately <b>abstract</b>: the use case knows only <em>that</em> the player should be
 * oriented (because their input was unrecognized, or because the session just opened), never the concrete
 * commands that orient them. That vocabulary is delivery-mechanism detail (design-notes §9) the presenter
 * owns — so these methods carry no command list, and the presenter decides what guidance (the command list,
 * styling) to render. Both outcomes share that command list, factored in the presenter so they cannot drift.
 */
public interface GuidancePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Outcome: the player's input matched no known command. The presenter renders the guidance — typically
     * echoing the input and listing the available commands — without the use case supplying any vocabulary.
     *
     * @param input the raw line the player typed that matched no known command
     */
    void presentUnrecognizedCommand(String input);

    /**
     * Outcome: the session has opened and the player is greeted. The presenter renders the welcome and the
     * available commands; the use case supplies no vocabulary.
     */
    void presentWelcome();
}
