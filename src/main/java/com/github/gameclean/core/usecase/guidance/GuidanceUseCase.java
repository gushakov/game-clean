package com.github.gameclean.core.usecase.guidance;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Guides a player who typed something unrecognized back toward their available actions. Implementation of
 * {@link GuidanceInputPort}; framework-free, wired by the composition root, exercised in isolation against a
 * mocked presenter.
 *
 * <p>The thinnest use case in the project: it reads no domain state, touches no transaction, and holds only
 * its presenter. Its single decision is that a lost player should be guided — so it presents the abstract
 * {@code presentUnrecognizedCommand} outcome, handing the raw input straight through for the presenter to
 * echo. The concrete command list is delivery-mechanism vocabulary owned by the presenter (design-notes §9),
 * never assembled here. It exists at all because controllers never present and a presenter must be driven by
 * a use case (design-notes §4); the outermost {@code catch} keeps flow unidirectional even for a body this
 * small. Exactly one {@code present*} is reached on every path.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GuidanceUseCase implements GuidanceInputPort {

    GuidancePresenterOutputPort presenter;

    @Override
    public void playerIssuesUnrecognizedCommand(String input) {
        try {
            // The input is not interpreted — the parser already determined it matched no command; it crosses
            // inward only so the presented guidance can echo it. The presenter owns the command vocabulary.
            presenter.presentUnrecognizedCommand(input);

        } catch (Exception e) {
            // Outermost checkpoint: only an unexpected bug could reach here (there is no I/O on this path).
            presenter.presentError(e);
        }
    }
}
