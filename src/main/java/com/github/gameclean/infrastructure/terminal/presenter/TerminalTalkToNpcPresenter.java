package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.core.usecase.parley.TalkToNpcPresenterOutputPort;
import com.github.gameclean.core.usecase.select.SelectTargetPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.NpcRenderer;
import com.github.gameclean.infrastructure.terminal.render.OfferRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Secondary (driven) adapter rendering the {@code TalkToNpc} use case's outcomes to the shared JLine console.
 * Like {@link TerminalFightNpcPresenter} it composes the shared renderers — {@link OrientRenderer} for the
 * orient not-founds, {@link NpcRenderer} for the speech and NPC-select outcomes, {@link OfferRenderer} for the
 * wares a vendor shows — and implements the three flat presenter ports the use case's collaborators drive
 * ({@code orient}, {@code select} bound to {@link Npc}, and {@code TalkToNpc}'s own), rather than extending a
 * base presenter.
 *
 * <p>Every outcome here is the <b>synchronous</b> response to the player's {@code talk} command, so it writes
 * to the prompt directly. Two outcomes arm the {@link AffordanceContext}: an ambiguous target arms it with
 * {@link AffordanceKind#TALK}, so a subsequent bare number resumes the conversation with the chosen NPC; shown
 * wares arm it with {@link AffordanceKind#BUY}, so a subsequent bare number buys the chosen ware — the menu is
 * armed by talk and resumed by the trade goal's purchase, which re-validates the token against the live scene.
 *
 * <p>The disambiguation menu is ordered here once (stable by short description, then id) and the same order is
 * both displayed and remembered, so the visible menu and the latent offer cannot drift — exactly as hit does.
 * The wares are not reordered: they are displayed and remembered in the authored order the use case presented.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalTalkToNpcPresenter
        implements OrientPlayerPresenterOutputPort, SelectTargetPresenterOutputPort<Npc>, TalkToNpcPresenterOutputPort {

    OrientRenderer orientRenderer;
    NpcRenderer npcRenderer;
    OfferRenderer offerRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentNpcSays(Npc npc, String saying) {
        npcRenderer.renderNpcSays(npc, saying);
    }

    @Override
    public void presentNpcRefusesToTalk(Npc npc) {
        npcRenderer.renderNpcRefusesToTalk(npc);
    }

    @Override
    public void presentNpcShowsWares(Npc npc, Optional<String> saying, List<StandingOffer> wares) {
        saying.ifPresent(line -> npcRenderer.renderNpcSays(npc, line));
        offerRenderer.renderWares(npc, wares);
        // Flatten the wares to their composite tokens in the order shown; tag the offer BUY so a later bare
        // number pays for the chosen ware.
        affordanceContext.offer(AffordanceKind.BUY, wares.stream().map(StandingOffer::idToken).toList());
    }

    @Override
    public void presentNpcHasNothingToSay(Npc npc) {
        npcRenderer.renderNpcHasNothingToSay(npc);
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
        // Flatten identities to tokens on this driven side; tag the offer TALK so a later bare number talks.
        affordanceContext.offer(AffordanceKind.TALK,
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
        log.error("[TalkToNpc] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
