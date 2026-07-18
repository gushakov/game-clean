package com.github.gameclean.core.model.blackjack;

import com.github.gameclean.core.model.DomainValidation;
import lombok.Value;

/**
 * One playing card — a rank and a suit. A Value Object: always-valid (both parts present), equality by value.
 * Its only blackjack behaviour is delegating {@link #pipValue()} to the rank; everything hand-shaped (soft
 * aces, busting) lives on {@link Hand}.
 */
@Value
public class Card {

    Rank rank;
    Suit suit;

    public Card(Rank rank, Suit suit) {
        this.rank = DomainValidation.requireNonNull(rank, "card rank must not be null");
        this.suit = DomainValidation.requireNonNull(suit, "card suit must not be null");
    }

    /**
     * The value this card contributes to a hand's hard total (ace counts one — softening is the hand's rule).
     *
     * @return the rank's pip value
     */
    public int pipValue() {
        return rank.getPipValue();
    }
}
