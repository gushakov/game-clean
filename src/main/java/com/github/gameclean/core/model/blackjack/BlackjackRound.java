package com.github.gameclean.core.model.blackjack;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Dice;
import lombok.Value;

/**
 * The state of one blackjack hand as it stands — the player's cards, the dealer's cards, and what remains of
 * the deck. A Value Object, deliberately: <em>a round is a position, not an entity</em> (see
 * {@code package-info}). It has no identity, no phase field, and knows nothing of who plays it; transitions are
 * copy-on-write side-effect-free functions, so everything after {@link #deal(Dice)} — the one stochastic
 * moment, whose shuffled deck this value captures — is pure and deterministic, including the whole dealer
 * playout.
 *
 * <p>House rules, hard-coded on purpose (the simplest enjoyable variant): a single deck; the dealer draws to
 * sixteen and stands on all seventeens; a player natural settles immediately against the dealer's dealt hand;
 * no split, double, surrender, insurance, or peek.
 */
@Value
public class BlackjackRound {

    Hand playerHand;
    Hand dealerHand;
    Deck deck;

    public BlackjackRound(Hand playerHand, Hand dealerHand, Deck deck) {
        this.playerHand = requireDealtHand(playerHand, "player");
        this.dealerHand = requireDealtHand(dealerHand, "dealer");
        this.deck = DomainValidation.requireNonNull(deck, "round deck must not be null");
    }

    /**
     * Opens a round: shuffles a full deck with the given dice and deals two cards to the player, then two to
     * the dealer — deck positions 0 and 1 to the player, 2 (the upcard) and 3 (the hole card) to the dealer.
     *
     * @param dice the entropy source for the shuffle — the round's only draw on chance
     * @return the dealt round
     */
    public static BlackjackRound deal(Dice dice) {
        Deck shuffled = Deck.freshlyShuffled(dice);
        Hand player = Hand.empty().plus(shuffled.top()).plus(shuffled.withoutTop().top());
        Deck afterPlayer = shuffled.withoutTop().withoutTop();
        Hand dealer = Hand.empty().plus(afterPlayer.top()).plus(afterPlayer.withoutTop().top());
        return new BlackjackRound(player, dealer, afterPlayer.withoutTop().withoutTop());
    }

    /**
     * The dealer's face-up card — the first of the dealer's dealt cards. The hole card is simply the rest of
     * the dealer's hand, which this value holds in full; what is <em>shown</em> is the presentation's concern.
     *
     * @return the dealer's upcard
     */
    public Card dealerUpcard() {
        return dealerHand.getCards().get(0);
    }

    /**
     * @return {@code true} when the player's hand overshoots twenty-one
     */
    public boolean isPlayerBust() {
        return playerHand.isBust();
    }

    /**
     * @return {@code true} when the player was dealt a natural — two cards making twenty-one
     */
    public boolean isNaturalBlackjack() {
        return playerHand.isBlackjack();
    }

    /**
     * The player takes another card off the top. Copy-on-write: a new round with the card in the player's hand
     * and the deck one shorter.
     *
     * @return the round after the draw
     * @throws IllegalStateException when the player is already bust — a busted round is terminal and is never
     *                               offered a further draw, so reaching this is a caller bug, not a player
     *                               outcome
     */
    public BlackjackRound playerDraws() {
        if (isPlayerBust()) {
            throw new IllegalStateException("the player is already bust — a terminal round takes no more draws");
        }
        return new BlackjackRound(playerHand.plus(deck.top()), dealerHand, deck.withoutTop());
    }

    /**
     * The dealer's whole playout, resolved in one pure step: draws while the hand's best value is sixteen or
     * less, stands on all seventeens. Consumes no entropy — the deck order was fixed at the deal — so the
     * playout is fully determined the moment the player stands.
     *
     * @return the round after the dealer has finished drawing
     */
    public BlackjackRound dealerPlaysOut() {
        Hand dealer = dealerHand;
        Deck remaining = deck;
        while (dealer.bestValue() <= 16) {
            dealer = dealer.plus(remaining.top());
            remaining = remaining.withoutTop();
        }
        return new BlackjackRound(playerHand, dealer, remaining);
    }

    /**
     * Settles the round by comparing the hands as they lie — a pure reading of this position, meaningful when
     * the round is terminal: the player busted, a natural settled at the deal, or the dealer has played out.
     *
     * @return the outcome of this round
     */
    public RoundOutcome settle() {
        if (playerHand.isBust()) {
            return RoundOutcome.PLAYER_BUSTED;
        }
        if (dealerHand.isBust()) {
            return RoundOutcome.DEALER_BUSTED;
        }
        int player = playerHand.bestValue();
        int dealer = dealerHand.bestValue();
        if (player > dealer) {
            return RoundOutcome.PLAYER_WINS;
        }
        if (player < dealer) {
            return RoundOutcome.DEALER_WINS;
        }
        return RoundOutcome.PUSH;
    }

    private static Hand requireDealtHand(Hand hand, String whose) {
        if (hand == null || hand.getCards().size() < 2) {
            throw new InvalidDomainObjectError(
                    "a round requires a dealt %s hand of at least two cards".formatted(whose));
        }
        return hand;
    }
}
