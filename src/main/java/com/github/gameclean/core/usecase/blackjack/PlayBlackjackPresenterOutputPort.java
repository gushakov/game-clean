package com.github.gameclean.core.usecase.blackjack;

import com.github.gameclean.core.model.blackjack.BlackjackRound;
import com.github.gameclean.core.model.blackjack.Card;
import com.github.gameclean.core.model.blackjack.Hand;
import com.github.gameclean.core.model.blackjack.RoundOutcome;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter output port for the {@code PlayBlackjack} use case — one method per outcome stripe. Beyond
 * rendering, the presenter owns the conversation's <em>arming transcription</em>, fixed per method: the
 * live-hand outcomes ({@link #presentInitialDeal}, {@link #presentCardDealt}, {@link #presentGameStanding})
 * arm/re-arm the session's affordance buffer with the round they carry; the terminal outcomes (natural, busted,
 * and the three settlements) disarm it — a completion-disarm, as deterministic a transcription as the arming,
 * distinct from the dispatcher's abandonment-clear. One present method, one fixed arming effect: the presenter
 * transcribes, it never decides.
 *
 * <p><b>Visibility is documented by the signatures.</b> While the hand is live, the rendered arguments are
 * exactly what the player may see — their own hand and the dealer's upcard; the full {@link BlackjackRound}
 * rides alongside <em>for arming only</em> (it holds the hole card and the deck, which the renderer must not
 * show). The terminal outcomes carry the round as the rendered payload too: the hand is over, everything is
 * face up.
 */
public interface PlayBlackjackPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** The scene the player stands in offers no blackjack — the sit-down is refused. */
    void presentNoOneDealsCardsHere(Scene scene);

    /**
     * The player was dealt a natural blackjack — the hand settles at the deal, everything face up.
     *
     * @param round   the settled position
     * @param outcome {@link RoundOutcome#PLAYER_WINS}, or {@link RoundOutcome#PUSH} when the dealer also
     *                holds twenty-one
     */
    void presentNaturalBlackjack(BlackjackRound round, RoundOutcome outcome);

    /**
     * The opening deal of a live hand: the player's cards and the dealer's upcard. Arms the hit-or-stand
     * affordance with the round.
     */
    void presentInitialDeal(Hand playerHand, Card upcard, BlackjackRound round);

    /**
     * A hit card left the hand live: the grown hand and the unchanged upcard. Re-arms the affordance with the
     * new position.
     */
    void presentCardDealt(Hand playerHand, Card upcard, BlackjackRound round);

    /** The hit card busted the player — the hand is over, the dealer collects. Disarms. */
    void presentPlayerBusted(BlackjackRound round);

    /** The dealer's playout busted — the player wins. Disarms. */
    void presentDealerBusted(BlackjackRound round);

    /** Both stood under twenty-two and the player's hand is higher. Disarms. */
    void presentPlayerWins(BlackjackRound round);

    /** Both stood under twenty-two and the dealer's hand is higher. Disarms. */
    void presentDealerWins(BlackjackRound round);

    /** Equal values — nobody wins. Disarms. */
    void presentPush(BlackjackRound round);

    /**
     * The anytime table view: the player's hand and the dealer's upcard as they stand. Re-arms the same
     * position, changing nothing.
     */
    void presentGameStanding(Hand playerHand, Card upcard, BlackjackRound round);
}
