package com.github.gameclean.core.usecase.guidance;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Guidance}, co-located with its use case. Extends the catch-all
 * {@link ErrorHandlingPresenterOutputPort#presentError(Exception)} and adds this goal's one outcome.
 *
 * <p>One outcome, {@link #presentUnrecognizedCommand(String)}. It is deliberately <b>abstract</b>: the use
 * case knows only that the player's input was not recognized, never the concrete commands that <em>are</em>.
 * That vocabulary is delivery-mechanism detail (design-notes §9) the presenter owns — so this method carries
 * just the raw input to echo, and the presenter decides what guidance (the command list, styling) to render
 * around it.
 */
public interface GuidancePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Outcome: the player's input matched no known command. The presenter renders the guidance — typically
     * echoing the input and listing the available commands — without the use case supplying any vocabulary.
     *
     * @param input the raw line the player typed that matched no known command
     */
    void presentUnrecognizedCommand(String input);
}
