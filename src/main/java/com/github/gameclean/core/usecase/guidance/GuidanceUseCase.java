package com.github.gameclean.core.usecase.guidance;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Orients the player toward their available actions — both when they type something unrecognized and when the
 * session opens. Implementation of {@link GuidanceInputPort}; framework-free, wired by the composition root,
 * exercised in isolation against a mocked presenter.
 *
 * <p>The thinnest use case in the project: it reads no domain state, touches no transaction, and holds only
 * its presenter. Each interaction's single decision is that the player should be oriented — so it presents an
 * abstract outcome ({@code presentUnrecognizedCommand} / {@code presentWelcome}), handing nothing but the raw
 * input (for the unrecognized case) through for the presenter to echo. The concrete command list both
 * outcomes show is delivery-mechanism vocabulary owned by the presenter (design-notes §9), never assembled
 * here. It exists at all because controllers never present and a presenter must be driven by a use case
 * (design-notes §4); each outermost {@code catch} keeps flow unidirectional even for bodies this small.
 * Exactly one {@code present*} is reached on every path.
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

    @Override
    public void systemGreetsPlayer() {
        try {
            // The system's session-opening turn: present the welcome. No input, no state — the presenter owns
            // the welcome text and the command vocabulary it lists.
            presenter.presentWelcome();

        } catch (Exception e) {
            // Outermost checkpoint: only an unexpected bug could reach here (there is no I/O on this path).
            presenter.presentError(e);
        }
    }
}
