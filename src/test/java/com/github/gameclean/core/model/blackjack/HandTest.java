package com.github.gameclean.core.model.blackjack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Pins the hand arithmetic — blackjack's showcase side-effect-free function. The soft-ace rule is the
 * interesting surface: an ace counts eleven exactly when that still fits, and only one ace can ever soften.
 */
class HandTest {

    private static Card card(Rank rank) {
        return new Card(rank, Suit.SPADES);
    }

    private static Hand hand(Rank... ranks) {
        return new Hand(List.of(ranks).stream().map(HandTest::card).toList());
    }

    @Test
    void best_value_sums_pips_when_no_ace_is_held() {
        assertThat(hand(Rank.TWO, Rank.KING).bestValue()).isEqualTo(12);
    }

    @Test
    void best_value_promotes_an_ace_to_eleven_when_it_fits() {
        assertThat(hand(Rank.ACE, Rank.SEVEN).bestValue()).isEqualTo(18);   // soft eighteen
    }

    @Test
    void best_value_keeps_the_ace_at_one_when_eleven_would_bust() {
        assertThat(hand(Rank.ACE, Rank.SEVEN, Rank.NINE).bestValue()).isEqualTo(17); // hard seventeen
    }

    @Test
    void best_value_promotes_only_one_of_several_aces() {
        assertThat(hand(Rank.ACE, Rank.ACE).bestValue()).isEqualTo(12);     // 11 + 1, never 22
        assertThat(hand(Rank.ACE, Rank.ACE, Rank.NINE).bestValue()).isEqualTo(21);
    }

    @Test
    void a_hand_over_twenty_one_is_bust() {
        assertThat(hand(Rank.KING, Rank.QUEEN, Rank.TWO).isBust()).isTrue();
        assertThat(hand(Rank.KING, Rank.QUEEN, Rank.ACE).isBust()).isFalse(); // the ace stays at one
    }

    @Test
    void a_natural_is_exactly_two_cards_making_twenty_one() {
        assertThat(hand(Rank.ACE, Rank.KING).isBlackjack()).isTrue();
        assertThat(hand(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN).isBlackjack()).isFalse(); // 21, but three cards
        assertThat(hand(Rank.KING, Rank.NINE).isBlackjack()).isFalse();
    }

    @Test
    void plus_deals_a_card_copy_on_write() {
        Hand two = hand(Rank.TWO, Rank.THREE);
        Hand three = two.plus(card(Rank.FOUR));
        assertThat(three.getCards()).hasSize(3);
        assertThat(two.getCards()).hasSize(2);
    }

    @Test
    void an_empty_hand_is_a_valid_starting_point() {
        assertThat(Hand.empty().getCards()).isEmpty();
        assertThat(Hand.empty().bestValue()).isZero();
    }
}
