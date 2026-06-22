package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.daytime.DayPhase;
import com.github.gameclean.core.usecase.clock.AnnounceTimeOfDayPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.Console;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Secondary (driven) adapter rendering the {@code AnnounceTimeOfDay} use case's outcome — the project's first
 * <b>asynchronous</b> presenter. The interaction is fired by a background actor (the time ticker) while the
 * player may be at the {@code game> } prompt, so the announcement is written with {@link Console#printAbove}
 * (above the live prompt), not {@link Console#write}.
 *
 * <p><b>A background actor mostly logs.</b> Only an actual day-phase announcement reaches the console; the
 * other outcomes would, if written to the console, spam it on every poll. So {@code presentNothingToAnnounce}
 * (the common quiet poll) and {@code presentGameNotInitialized} (the ticker firing before the world is seeded)
 * are trace logs, and {@code presentError} is an operator-facing log — never repeated console noise. This is
 * the §4 rule "a presenter is mandated even with no human audience — so it logs" applied to a system actor.
 *
 * <p>Like the other terminal presenters it is {@code new}ed by the composition root (not a {@code @Component}),
 * sharing the {@code Console} resource.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalAnnounceTimeOfDayPresenter implements AnnounceTimeOfDayPresenterOutputPort {

    private static final AttributedStyle PHASE = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();

    Console console;

    @Override
    public void presentDayPhaseBegan(DayPhase phase, String message) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(PHASE).append(phase.getName())
                .style(AttributedStyle.DEFAULT).append(". ")
                .append(message);
        console.printAbove(sb);
    }

    @Override
    public void presentNothingToAnnounce() {
        log.trace("[AnnounceTimeOfDay] No day phase to announce on this observation");
    }

    @Override
    public void presentGameNotInitialized() {
        // The ticker can fire before the world is seeded; not a player-facing condition — keep it off the console.
        log.trace("[AnnounceTimeOfDay] Observed time before the game was initialized; nothing to announce");
    }

    @Override
    public void presentError(Exception e) {
        // A background actor: log for the operator rather than spamming the console every interval.
        log.warn("[AnnounceTimeOfDay] Unexpected error while observing the time of day", e);
    }
}
