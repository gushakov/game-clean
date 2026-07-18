package com.github.gameclean.core.usecase.blackjack;

import com.github.gameclean.core.model.blackjack.BlackjackRound;

/**
 * Driving (input) port for the <b>play blackjack</b> user goal: the player plays one hand of blackjack to its
 * end against the dealer persona. A multi-interaction conversation whose steps are the methods below; every
 * interaction is initiated by the <em>player</em>, and every outcome is presented through
 * {@link PlayBlackjackPresenterOutputPort}, never returned.
 *
 * <p><b>The round travels as a value.</b> The interactions after the deal take the current
 * {@link BlackjackRound} as a parameter: the driving adapter relays the position the previous interaction's
 * presenter parked in the session's affordance buffer — opaquely, never reading it — and this use case computes
 * the next position and presents it (dependency rejection, the {@code examine} offered-tokens pattern carrying
 * substance instead of tokens). The round is system-authored and valid by provenance, so no construction gate
 * applies; a null (or busted) round reaching an interaction means the dispatcher resumed an unarmed or finished
 * conversation — a wiring fault, thrown to the catch-all, not a player outcome.
 *
 * <p>Main success scenario: sit down (deal; the affordance becomes hit-or-stand) → zero or more hit cards →
 * stand → the dealer's synchronous playout and settlement. Extensions: the scene deals no cards (sit-down
 * refused); a natural blackjack settles at the deal; a hit card busts the player; and the <em>anytime</em>
 * extension {@link #playerExaminesGame} ("*a. at any time, the player may ask where the game stands").
 */
public interface PlayBlackjackInputPort {

    /**
     * The player sits down at the table and asks to be dealt in. Grounds the goal — the player must stand in a
     * scene whose authored offering includes blackjack — then shuffles and deals (the conversation's one draw
     * on chance). A natural blackjack settles immediately; otherwise the initial deal is presented and the
     * hit-or-stand affordance armed. Sitting down mid-hand cannot reach this interaction: while a round is
     * armed, the driving adapter routes {@code play} to {@link #playerExaminesGame} instead (re-presenting the
     * table is the helpful answer).
     */
    void playerSitsDownToPlay();

    /**
     * The player asks the dealer for a hit card. The dealt card either busts the player — settling the hand —
     * or leaves it live, re-presenting the hand and re-arming the affordance with the new position.
     *
     * @param round the current position, relayed by the driving adapter from the armed affordance
     */
    void playerAsksDealerForHitCard(BlackjackRound round);

    /**
     * The player stands. The dealer's whole playout resolves synchronously — and, because the deal captured the
     * deck, deterministically — and the hand settles into exactly one presented outcome: dealer busts, player
     * wins, dealer wins, or push. All terminal: the affordance is disarmed by the presentation.
     *
     * @param round the current position, relayed by the driving adapter from the armed affordance
     */
    void playerRequestsToStand(BlackjackRound round);

    /**
     * Anytime extension: the player asks where the game stands. Stateless and repeatable — re-presents the
     * table (the player's hand and the dealer's upcard; the hole card stays down) and re-arms the same
     * position, changing nothing.
     *
     * @param round the current position, relayed by the driving adapter from the armed affordance
     */
    void playerExaminesGame(BlackjackRound round);
}
