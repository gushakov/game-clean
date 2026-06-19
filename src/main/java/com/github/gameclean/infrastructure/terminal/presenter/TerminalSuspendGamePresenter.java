package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.usecase.clock.SuspendGamePresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.Console;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Secondary (driven) adapter for the {@code SuspendGame} use case: it writes the parting acknowledgement that
 * the player's session time has been banked, to the shared JLine console. It is presented after the bank
 * commits, so the player is never told their progress is saved before it durably is.
 *
 * <p>Like the other terminal presenters it is {@code new}ed by the composition root (not a {@code @Component}),
 * sharing the {@code Console} resource.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalSuspendGamePresenter implements SuspendGamePresenterOutputPort {

    Console console;

    @Override
    public void presentGameSuspended() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                .append("Your journey is saved. Farewell.");
        console.write(sb);
    }

    @Override
    public void presentGameNotInitialized() {
        console.printError("The game has not been initialized yet.");
    }

    @Override
    public void presentError(Exception e) {
        log.error("[SuspendGame] Unexpected error", e);
        console.printError("Something went wrong while saving your progress.");
    }
}
