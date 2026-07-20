package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.combat.HitPresenterOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.core.usecase.select.SelectTargetPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.SelectionKind;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.NpcRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code Hit} use case's outcomes to the shared JLine console. Like
 * {@link TerminalTakePresenter} it composes the shared renderers — {@link OrientRenderer} for the inherited
 * orient not-founds, {@link NpcRenderer} for the combat and NPC-select outcomes — and implements the three flat
 * presenter ports the use case's collaborators drive ({@code orient}, {@code select} bound to {@link Npc}, and
 * {@code hit}'s own), rather than extending a base presenter.
 *
 * <p>It differs from the take presenter on exactly two axes: its terminal outcomes are <em>combat</em> outcomes
 * ({@link #presentNpcStruck}, {@link #presentNpcSlain}, {@link #presentNpcGotAway}) rather than item ones, and
 * it arms the {@link AffordanceContext} with {@link SelectionKind#HIT} so a subsequent bare number resumes
 * <em>striking</em>. The disambiguation menu is ordered here once (stable by short description, then id) and the
 * same order is both displayed and remembered, so the visible menu and the latent offer cannot drift — exactly
 * as take does it.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalHitPresenter
        implements OrientPlayerPresenterOutputPort, SelectTargetPresenterOutputPort<Npc>, HitPresenterOutputPort {

    OrientRenderer orientRenderer;
    NpcRenderer npcRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentNpcStruck(Npc npc, int damage) {
        npcRenderer.renderNpcStruck(npc, damage);
    }

    @Override
    public void presentNpcSlain(Npc npc) {
        npcRenderer.renderNpcSlain(npc);
    }

    @Override
    public void presentNpcGotAway(NpcId npcId) {
        npcRenderer.renderNpcGotAway(npcId);
    }

    @Override
    public void presentNoSuchTarget(String target) {
        npcRenderer.renderNoSuchTarget(target);
    }

    @Override
    public void presentAmbiguousTarget(String target, List<Npc> candidates) {
        // Decide the menu order once, here — the visible menu and the remembered offer are produced from it.
        List<Npc> ordered = candidates.stream()
                .sorted(Comparator.comparing(Npc::getShortDescription)
                        .thenComparing(npc -> npc.getId().asString()))
                .toList();
        npcRenderer.renderAmbiguousTarget(target, ordered);
        // Flatten identities to tokens on this driven side; tag the offer HIT so a later bare number strikes.
        affordanceContext.offer(SelectionKind.HIT,
                ordered.stream().map(npc -> npc.getId().asString()).toList());
    }

    @Override
    public void presentTargetNoLongerAvailable(String idToken) {
        npcRenderer.renderTargetNoLongerAvailable(idToken);
    }

    @Override
    public void presentNoSuchOption(int ordinal) {
        npcRenderer.renderNoSuchOption(ordinal);
    }

    @Override
    public void presentPlayerNotFound(PlayerId playerId) {
        orientRenderer.renderPlayerNotFound(playerId);
    }

    @Override
    public void presentCurrentSceneNotFound(SceneId sceneId) {
        orientRenderer.renderCurrentSceneNotFound(sceneId);
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Hit] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
