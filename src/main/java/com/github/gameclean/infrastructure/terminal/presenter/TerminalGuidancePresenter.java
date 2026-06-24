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
 * Secondary (driven) adapter for the {@code Guidance} use case: it tells a player who typed something
 * unrecognized what they can do, on the shared JLine console.
 *
 * <p>This presenter <b>owns the concrete command vocabulary</b>. The use case presents only the abstract
 * "unrecognized" outcome with the raw input; the list of commands the player <em>can</em> type is
 * delivery-mechanism detail (design-notes §9), so it is a curated, player-friendly string maintained here —
 * deliberately not a raw dump of the parser's registered verbs (which would leak synonyms like {@code x} and
 * {@code go}). The guidance is styled as an advisory (yellow), not an error: an unrecognized command is a
 * misstep to steer, not a failure.
 *
 * <p>Like the other terminal presenters it is {@code new}ed by the composition root (not a {@code @Component}),
 * sharing the {@code Console} resource.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalGuidancePresenter implements GuidancePresenterOutputPort {

    Console console;

    @Override
    public void presentUnrecognizedCommand(String input) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                .append("I don't understand '%s'. Try: ".formatted(input))
                .append("'look', 'look <target>' / 'examine <target>', 'move <exit>', 'now', or 'bye'.");
        console.write(sb);
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Guidance] Unexpected error", e);
        console.printError("Something went wrong.");
    }
}
