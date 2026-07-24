package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.usecase.npc.AnimateNpcsPresenterOutputPort;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Secondary (driven) adapter rendering the {@code AnimateNpcs} policy's outcome. Since the use case became a
 * read-only policy that <em>dispatches</em> commands (issue #66 step 2), it narrates nothing itself — each
 * dispatched action narrates its own outcome mid-run through its own executing interaction's presenter (a wander
 * through {@code Wander}, a strike through {@code FightNpc}). So this presenter has nothing to write to the
 * console: its single quiet outcome is a trace log, and {@code presentError} an operator-facing log — never
 * repeated console noise. This is the §4 rule "a presenter is mandated even with no human audience — so it logs"
 * applied to a system-actor policy.
 *
 * <p>Like the other terminal presenters it is {@code new}ed by the composition root (not a {@code @Component});
 * it now needs no renderer at all.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class TerminalAnimateNpcsPresenter implements AnimateNpcsPresenterOutputPort {

    @Override
    public void presentNothingHappened() {
        log.trace("[AnimateNpcs] Policy tick decided and dispatched; nothing for the policy itself to narrate");
    }

    @Override
    public void presentError(Exception e) {
        // A background policy: log for the operator rather than spamming the console every tick.
        log.warn("[AnimateNpcs] Unexpected error while advancing NPCs", e);
    }
}
