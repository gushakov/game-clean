package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.blackjack.BlackjackRound;
import com.github.gameclean.core.model.blackjack.Card;
import com.github.gameclean.core.model.blackjack.Hand;
import com.github.gameclean.core.model.blackjack.RoundOutcome;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.blackjack.PlayBlackjackPresenterOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import com.github.gameclean.infrastructure.terminal.render.BlackjackRenderer;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Secondary (driven) adapter rendering the {@code PlayBlackjack} use case's outcomes to the shared JLine
 * console. Like its siblings it composes the shared renderers — {@link OrientRenderer} for the orient
 * not-founds, {@link BlackjackRenderer} for the table talk — and implements the two flat presenter
 * ports the use case drives, rather than extending a base presenter.
 *
 * <p><b>It owns the conversation's whole arming transcription, fixed per method.</b> The live-hand outcomes
 * ({@code presentInitialDeal}, {@code presentCardDealt}, {@code presentGameStanding}) render the visible face
 * and arm the {@link AffordanceContext} (kind {@link AffordanceKind#BLACKJACK}) with the round as the opaque
 * envelope — the latent face; the terminal outcomes (natural, busted, the settlements) render everything face
 * up and <em>disarm</em> — the completion-disarm, distinct from the dispatcher's abandonment-clear. One present
 * method, one deterministic arming effect: this presenter transcribes what the use case decided, it never
 * decides. The envelope written here is what the driving side later relays back into the input port unopened —
 * the round-trips-as-a-value loop the blackjack conversation runs on.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalPlayBlackjackPresenter
        implements OrientPlayerPresenterOutputPort, PlayBlackjackPresenterOutputPort {

    OrientRenderer orientRenderer;
    BlackjackRenderer blackjackRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentNoOneDealsCardsHere(Scene scene) {
        blackjackRenderer.renderNoOneDealsCardsHere(scene);
    }

    @Override
    public void presentNaturalBlackjack(BlackjackRound round, RoundOutcome outcome) {
        blackjackRenderer.renderNaturalBlackjack(round, outcome);
        affordanceContext.clear();
    }

    @Override
    public void presentInitialDeal(Hand playerHand, Card upcard, BlackjackRound round) {
        blackjackRenderer.renderInitialDeal(playerHand, upcard);
        affordanceContext.arm(AffordanceKind.BLACKJACK, round);
    }

    @Override
    public void presentCardDealt(Hand playerHand, Card upcard, BlackjackRound round) {
        blackjackRenderer.renderCardDealt(playerHand, upcard);
        affordanceContext.arm(AffordanceKind.BLACKJACK, round);
    }

    @Override
    public void presentPlayerBusted(BlackjackRound round) {
        blackjackRenderer.renderPlayerBusted(round);
        affordanceContext.clear();
    }

    @Override
    public void presentDealerBusted(BlackjackRound round) {
        blackjackRenderer.renderDealerBusted(round);
        affordanceContext.clear();
    }

    @Override
    public void presentPlayerWins(BlackjackRound round) {
        blackjackRenderer.renderPlayerWins(round);
        affordanceContext.clear();
    }

    @Override
    public void presentDealerWins(BlackjackRound round) {
        blackjackRenderer.renderDealerWins(round);
        affordanceContext.clear();
    }

    @Override
    public void presentPush(BlackjackRound round) {
        blackjackRenderer.renderPush(round);
        affordanceContext.clear();
    }

    @Override
    public void presentGameStanding(Hand playerHand, Card upcard, BlackjackRound round) {
        blackjackRenderer.renderGameStanding(playerHand, upcard);
        affordanceContext.arm(AffordanceKind.BLACKJACK, round);
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
        log.error("[PlayBlackjack] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
