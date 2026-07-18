package com.github.gameclean.core.model.blackjack;

import com.github.gameclean.core.model.DomainValidation;
import lombok.Value;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The cards in front of one participant, in the order they were dealt. A Value Object: immutable
 * ({@link #plus(Card)} yields a new hand), equality by value, and the owner of blackjack's hand arithmetic —
 * {@link #bestValue()} with the soft-ace rule, {@link #isBust()}, {@link #isBlackjack()}. May be empty (a hand
 * builds up card by card during the deal); a <em>round</em> requires dealt hands, but that is
 * {@link BlackjackRound}'s invariant, not this one's.
 */
@Value
public class Hand {

    List<Card> cards;

    public Hand(List<Card> cards) {
        this.cards = List.copyOf(DomainValidation.requireNonNull(cards, "hand cards must not be null"));
    }

    /**
     * An empty hand — the state before any card is dealt.
     *
     * @return a hand holding no cards
     */
    public static Hand empty() {
        return new Hand(List.of());
    }

    /**
     * This hand with one more card dealt onto it. Copy-on-write: returns a new hand, this one unchanged.
     *
     * @param card the card dealt (must not be null — a caller programming error, not invalid domain input)
     * @return a new hand with the card appended
     */
    public Hand plus(Card card) {
        Objects.requireNonNull(card, "card must not be null");
        return new Hand(concat(cards, card));
    }

    /**
     * The best blackjack value of this hand — the showcase side-effect-free function. The hard total counts
     * every ace as one; if the hand holds an ace and promoting <em>one</em> of them to eleven still fits within
     * twenty-one, the value is that soft total (promoting a second ace would always bust: 11 + 11 &gt; 21).
     *
     * @return the highest value of this hand that does not overshoot, or the hard total when even it busts
     */
    public int bestValue() {
        int hardTotal = cards.stream().mapToInt(Card::pipValue).sum();
        boolean holdsAce = cards.stream().anyMatch(card -> card.getRank() == Rank.ACE);
        if (holdsAce && hardTotal + 10 <= 21) {
            return hardTotal + 10;
        }
        return hardTotal;
    }

    /**
     * @return {@code true} when even the best value overshoots twenty-one
     */
    public boolean isBust() {
        return bestValue() > 21;
    }

    /**
     * A natural blackjack: exactly two cards making twenty-one (necessarily an ace and a ten-valued card).
     *
     * @return {@code true} when this hand is a natural
     */
    public boolean isBlackjack() {
        return cards.size() == 2 && bestValue() == 21;
    }

    private static List<Card> concat(List<Card> cards, Card extra) {
        return Stream.concat(cards.stream(), Stream.of(extra)).toList();
    }
}
