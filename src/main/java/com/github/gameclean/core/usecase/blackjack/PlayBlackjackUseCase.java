package com.github.gameclean.core.usecase.blackjack;

import com.github.gameclean.core.model.blackjack.BlackjackRound;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.scene.MiniGame;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Plays one hand of blackjack to its end. Implementation of {@link PlayBlackjackInputPort}; framework-free,
 * wired by the composition root, exercised in isolation against a mocked presenter and orient subcase (a
 * {@code ScriptedDice} — whose shuffle is the identity — pins the deal).
 *
 * <p><b>The thinnest orchestration in the project after {@code Guidance}: no persistence port, no transaction
 * port.</b> The whole conversation is read-free and write-free — the round is handed in as a value, the next
 * round is computed by the {@code BlackjackRound} position VO (the functional core; this class only selects the
 * outcome stripe), and it leaves through the presenter, whose arming is the state's only custody. Only the
 * sit-down grounds itself in the world, through the shared {@code orient} prologue.
 *
 * <p><b>Preconditions vs. outcomes.</b> A null round, or a busted one, reaching a mid-hand interaction is a
 * <em>wiring fault</em> — the dispatcher resumes only an armed conversation, and terminal presentations disarm —
 * so {@link #requireLiveRound} throws to the outermost {@code catch → presentError} (the select subcase's
 * empty-offer precedent) rather than presenting a player outcome.
 *
 * <p>On every path exactly one {@code present*} is reached, as the interaction's last act: the orient subcase
 * presents its own failures and throws {@link SubcaseAlreadyPresented} (swallowed as a no-op); each interaction
 * body branches to exactly one stripe; the outermost {@code catch} routes anything unhandled to
 * {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class PlayBlackjackUseCase implements PlayBlackjackInputPort {

    PlayBlackjackPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    Dice dice;

    @Override
    public void playerSitsDownToPlay() {
        try {
            // Grounding: the player must stand where cards are dealt (Tell-Don't-Ask on the scene).
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            if (!bearings.getScene().offers(MiniGame.BLACKJACK)) {
                presenter.presentNoOneDealsCardsHere(bearings.getScene());
                return;
            }

            // The deal — the conversation's single draw on chance; the shuffled deck is captured in the
            // round, so everything after this line is deterministic (capture-at-offer).
            BlackjackRound round = BlackjackRound.deal(dice);

            if (round.isNaturalBlackjack()) {
                presenter.presentNaturalBlackjack(round, round.settle());
                return;
            }
            presenter.presentInitialDeal(round.getPlayerHand(), round.dealerUpcard(), round);

        } catch (SubcaseAlreadyPresented e) {
            // The orient subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerAsksDealerForHitCard(BlackjackRound round) {
        try {
            requireLiveRound(round);
            BlackjackRound drawn = round.playerDraws();
            if (drawn.isPlayerBust()) {
                presenter.presentPlayerBusted(drawn);
                return;
            }
            presenter.presentCardDealt(drawn.getPlayerHand(), drawn.dealerUpcard(), drawn);
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerRequestsToStand(BlackjackRound round) {
        try {
            requireLiveRound(round);
            BlackjackRound played = round.dealerPlaysOut();
            switch (played.settle()) {
                case DEALER_BUSTED -> presenter.presentDealerBusted(played);
                case PLAYER_WINS -> presenter.presentPlayerWins(played);
                case DEALER_WINS -> presenter.presentDealerWins(played);
                case PUSH -> presenter.presentPush(played);
                // Unreachable: requireLiveRound barred a busted round. Kept for the exhaustive switch.
                case PLAYER_BUSTED -> throw new IllegalStateException(
                        "a busted round cannot reach the stand interaction");
            }
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerExaminesGame(BlackjackRound round) {
        try {
            requireLiveRound(round);
            presenter.presentGameStanding(round.getPlayerHand(), round.dealerUpcard(), round);
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * Wiring precondition, not a player outcome: the dispatcher resumes a blackjack conversation only while a
     * live round is armed, and terminal presentations disarm — so a null or busted round arriving here means
     * the routing (or an arming transcription) is broken. Throw to the catch-all rather than presenting;
     * presenting "you're not playing" would mislabel a programming error as a player mistake.
     */
    private static void requireLiveRound(BlackjackRound round) {
        if (round == null) {
            throw new IllegalStateException(
                    "blackjack interaction invoked with no armed round — the dispatcher must resume only an armed conversation");
        }
        if (round.isPlayerBust()) {
            throw new IllegalStateException(
                    "blackjack interaction invoked with a busted round — terminal presentations must disarm");
        }
    }
}
