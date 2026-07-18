package com.github.gameclean.core.model.blackjack;

/**
 * How a finished round stands — the value {@link BlackjackRound#settle()} computes by comparing the hands as
 * they lie. Five outcomes rather than three because <em>how</em> a side lost is part of the result a player is
 * told: busting is not the same news as being outdrawn.
 */
public enum RoundOutcome {
    /** The player overshot twenty-one; the dealer never needs to play. */
    PLAYER_BUSTED,
    /** The dealer overshot twenty-one during the playout; the player wins. */
    DEALER_BUSTED,
    /** Both stood under twenty-two and the player's hand is the higher. */
    PLAYER_WINS,
    /** Both stood under twenty-two and the dealer's hand is the higher. */
    DEALER_WINS,
    /** Equal values — nobody wins. */
    PUSH
}
