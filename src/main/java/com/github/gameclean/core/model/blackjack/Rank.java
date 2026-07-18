package com.github.gameclean.core.model.blackjack;

import lombok.Getter;

/**
 * The thirteen ranks, each carrying its blackjack <em>pip value</em> — the value a card of that rank contributes
 * to a hand's hard total. Faces count ten; the ace's pip value is one, and the "ace may count eleven" rule is
 * deliberately <em>not</em> here: whether an ace softens to eleven depends on the rest of the hand, so it is
 * {@link Hand#bestValue()}'s rule, not the rank's.
 */
@Getter
public enum Rank {
    TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10),
    JACK(10), QUEEN(10), KING(10),
    ACE(1);

    private final int pipValue;

    Rank(int pipValue) {
        this.pipValue = pipValue;
    }
}
