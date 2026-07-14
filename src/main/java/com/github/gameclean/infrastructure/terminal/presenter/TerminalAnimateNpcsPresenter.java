package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.usecase.npc.AnimateNpcsPresenterOutputPort;
import com.github.gameclean.core.usecase.npc.PerceivedNpcMovement;
import com.github.gameclean.infrastructure.terminal.render.NpcRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code AnimateNpcs} use case's outcome — an <b>asynchronous</b>
 * presenter, the NPC peer of {@link TerminalAnnounceTimeOfDayPresenter}. The interaction is fired by a
 * background actor (the NPC-activity ticker) while the player may be at the {@code game> } prompt, so witnessed
 * movements are narrated above the live prompt (via {@link NpcRenderer}, which uses {@code Console.printAbove}).
 *
 * <p><b>A background actor mostly logs.</b> Only actual perceptible movement reaches the console; the quiet
 * outcome would, if written, spam it on every tick. So {@code presentNothingHappened} (the common quiet tick,
 * and the safe pre-initialization case) is a trace log, and {@code presentError} is an operator-facing log —
 * never repeated console noise. This is the §4 rule "a presenter is mandated even with no human audience — so
 * it logs" applied to a system actor.
 *
 * <p>Like the other terminal presenters it is {@code new}ed by the composition root (not a {@code @Component}),
 * delegating its one console outcome to the shared {@link NpcRenderer} resource.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalAnimateNpcsPresenter implements AnimateNpcsPresenterOutputPort {

    NpcRenderer npcRenderer;

    @Override
    public void presentNpcMovements(List<PerceivedNpcMovement> movements) {
        npcRenderer.renderMovements(movements);
    }

    @Override
    public void presentNothingHappened() {
        log.trace("[AnimateNpcs] No perceptible NPC movement on this tick");
    }

    @Override
    public void presentError(Exception e) {
        // A background actor: log for the operator rather than spamming the console every tick.
        log.warn("[AnimateNpcs] Unexpected error while advancing NPCs", e);
    }
}
