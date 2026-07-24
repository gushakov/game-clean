package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.usecase.npc.PerceivedNpcMovement;
import com.github.gameclean.core.usecase.npc.WanderPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.NpcRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Secondary (driven) adapter rendering the {@code Wander} use case's outcome — an <b>asynchronous</b> presenter,
 * the peer of {@link TerminalAnnounceTimeOfDayPresenter} and the successor of the movement half of the old
 * animate presenter. A wander is executed by a background session (running a command the policy dispatched)
 * while the player may be at the {@code game> } prompt, so a witnessed movement is narrated above the live
 * prompt (via {@link NpcRenderer#renderMovement}, which uses {@code Console.printAbove}).
 *
 * <p><b>A background actor mostly logs.</b> Only an actually perceptible wander reaches the console; the quiet
 * outcome (off-stage move, or a no-op execution) would, if written, spam it. So {@code presentNothingHappened}
 * is a trace log and {@code presentError} an operator-facing log — never repeated console noise. Like the other
 * terminal presenters it is {@code new}ed by the composition root, delegating its one console outcome to the
 * shared {@link NpcRenderer} resource.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalWanderPresenter implements WanderPresenterOutputPort {

    NpcRenderer npcRenderer;

    @Override
    public void presentNpcMovement(PerceivedNpcMovement movement) {
        npcRenderer.renderMovement(movement);
    }

    @Override
    public void presentNothingHappened() {
        log.trace("[Wander] No perceptible NPC movement on this execution");
    }

    @Override
    public void presentError(Exception e) {
        // A background actor: log for the operator rather than spamming the console.
        log.warn("[Wander] Unexpected error while wandering an NPC", e);
    }
}
