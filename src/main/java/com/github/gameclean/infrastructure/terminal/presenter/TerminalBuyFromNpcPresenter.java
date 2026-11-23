package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.core.usecase.select.SelectTargetPresenterOutputPort;
import com.github.gameclean.core.usecase.trade.BuyFromNpcPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.OfferRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code BuyFromNpc} use case's outcomes to the shared JLine console.
 * Like {@link TerminalTalkToNpcPresenter} it composes the shared renderers — {@link OrientRenderer} for the
 * orient not-founds, {@link OfferRenderer} for the transaction and ware-select outcomes — and implements the
 * three flat presenter ports the use case's collaborators drive ({@code orient}, {@code select} bound to
 * {@link StandingOffer}, and {@code BuyFromNpc}'s own), rather than extending a base presenter.
 *
 * <p>Every outcome here is the <b>synchronous</b> response to the player's {@code buy} (or {@code rent})
 * command, so it writes to the prompt directly; an ambiguous ware arms the {@link AffordanceContext} with
 * {@link AffordanceKind#BUY} so a subsequent bare number resumes the purchase of the chosen ware. The menu a
 * purchase resumes may instead have been armed by {@code TerminalTalkToNpcPresenter}, when a vendor showed its
 * wares; the outcomes of that resumed purchase are rendered here all the same.
 *
 * <p>The disambiguation menu is ordered here once (stable by offer name, then composite token) and the same
 * order is both displayed and remembered, so the visible menu and the latent offer cannot drift — exactly as
 * talk and hit do. The tokens armed are the wares' composite {@code vendorId:offerRef} handles, the form
 * {@link StandingOffer#hasIdToken} re-confirms against live state.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalBuyFromNpcPresenter
        implements OrientPlayerPresenterOutputPort, SelectTargetPresenterOutputPort<StandingOffer>,
        BuyFromNpcPresenterOutputPort {

    OrientRenderer orientRenderer;
    OfferRenderer offerRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentWareBought(StandingOffer ware, Item delivered) {
        offerRenderer.renderWareBought(ware, delivered);
    }

    @Override
    public void presentNotEnoughCoins(StandingOffer ware, int coinsHeld) {
        offerRenderer.renderNotEnoughCoins(ware, coinsHeld);
    }

    @Override
    public void presentNoSuchTarget(String target) {
        offerRenderer.renderNoSuchWare(target);
    }

    @Override
    public void presentAmbiguousTarget(String target, List<StandingOffer> candidates) {
        // Decide the menu order once, here — the visible menu and the remembered offer are produced from it.
        List<StandingOffer> ordered = candidates.stream()
                .sorted(Comparator.comparing((StandingOffer ware) -> ware.getOffer().getName())
                        .thenComparing(StandingOffer::idToken))
                .toList();
        offerRenderer.renderAmbiguousWare(target, ordered);
        // Flatten composites to tokens on this driven side; tag the offer BUY so a later bare number pays.
        affordanceContext.offer(AffordanceKind.BUY,
                ordered.stream().map(StandingOffer::idToken).toList());
    }

    @Override
    public void presentTargetNoLongerAvailable(String idToken) {
        offerRenderer.renderWareNoLongerAvailable();
    }

    @Override
    public void presentNoSuchOption(int ordinal) {
        offerRenderer.renderNoSuchOption(ordinal);
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
        log.error("[BuyFromNpc] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
