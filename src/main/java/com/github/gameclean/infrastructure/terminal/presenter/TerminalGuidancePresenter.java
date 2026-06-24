package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.usecase.guidance.GuidancePresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.Console;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Secondary (driven) adapter for the {@code Guidance} use case: it tells the player what they can do — both
 * the session-opening welcome and the nudge after an unrecognized command — on the shared JLine console.
 *
 * <p>This presenter <b>owns the concrete command vocabulary</b>. The use case presents only abstract outcomes
 * ({@code presentWelcome} / {@code presentUnrecognizedCommand}); the list of commands the player <em>can</em>
 * type is delivery-mechanism detail (design-notes §9), so it is a curated, player-friendly string maintained
 * here — deliberately not a raw dump of the parser's registered verbs (which would leak synonyms like
 * {@code x} and {@code go}). Both outcomes show that one list, so it is factored into a single constant and
 * they cannot drift. The welcome is styled as a greeting (cyan); the unrecognized-command nudge as an advisory
 * (yellow), not an error — a misstep to steer, not a failure.
 *
 * <p>Like the other terminal presenters it is {@code new}ed by the composition root (not a {@code @Component}),
 * sharing the {@code Console} resource.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalGuidancePresenter implements GuidancePresenterOutputPort {

    /** The curated, player-facing command list, shared by both outcomes so they cannot drift. */
    private static final String AVAILABLE_COMMANDS =
            "'look', 'look <target>' / 'examine <target>', 'move <exit>', 'now', 'bye'";

    Console console;

    @Override
    public void presentWelcome() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("Welcome to game-clean. Available commands: ")
                .append(AVAILABLE_COMMANDS)
                .append(".");
        console.write(sb);
    }

    @Override
    public void presentUnrecognizedCommand(String input) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                .append("I don't understand '%s'. Available commands: ".formatted(input))
                .append(AVAILABLE_COMMANDS)
                .append(".");
        console.write(sb);
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Guidance] Unexpected error", e);
        console.printError("Something went wrong.");
    }
}
