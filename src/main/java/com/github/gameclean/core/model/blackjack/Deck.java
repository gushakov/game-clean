package com.github.gameclean.core.model.blackjack;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.dice.Dice;
import lombok.Value;

import java.util.Arrays;
import java.util.List;

/**
 * An ordered stack of cards, drawn from the top (index zero). A Value Object: immutable — drawing is
 * {@link #top()} plus {@link #withoutTop()}, each read side-effect-free. The canonical fifty-two-card deck comes
 * from {@link #freshlyShuffled(Dice)}; the public constructor takes any card sequence so a test (or a rigged
 * dealer, some day) can hand-craft an exact order — a deck is an ordered sequence, not a completeness invariant.
 */
@Value
public class Deck {

    List<Card> cards;

    public Deck(List<Card> cards) {
        this.cards = List.copyOf(DomainValidation.requireNonNull(cards, "deck cards must not be null"));
    }

    /**
     * A full fifty-two-card deck in a uniformly random order rolled by the given dice. This is the round's one
     * stochastic moment: once shuffled, every later draw is determined (capture-at-offer).
     *
     * @param dice the entropy source ordering the deck
     * @return a freshly shuffled full deck
     */
    public static Deck freshlyShuffled(Dice dice) {
        List<Card> fullDeck = Arrays.stream(Suit.values())
                .flatMap(suit -> Arrays.stream(Rank.values()).map(rank -> new Card(rank, suit)))
                .toList();
        return new Deck(dice.shuffle(fullDeck));
    }

    /**
     * The next card to be dealt. Reading does not draw — pair with {@link #withoutTop()}.
     *
     * @return the top card
     * @throws IllegalStateException on an empty deck — unreachable in a blackjack round (hands bust or stand
     *                               long before fifty-two cards run out), so an empty draw is a caller bug
     */
    public Card top() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("cannot draw from an empty deck");
        }
        return cards.get(0);
    }

    /**
     * This deck with the top card drawn off. Copy-on-write: returns a new deck, this one unchanged.
     *
     * @return a new deck without the top card
     * @throws IllegalStateException on an empty deck (see {@link #top()})
     */
    public Deck withoutTop() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("cannot draw from an empty deck");
        }
        return new Deck(cards.subList(1, cards.size()));
    }

    /**
     * @return how many cards remain
     */
    public int size() {
        return cards.size();
    }
}
