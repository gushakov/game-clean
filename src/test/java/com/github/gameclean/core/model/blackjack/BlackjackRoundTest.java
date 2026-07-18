package com.github.gameclean.core.model.blackjack;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.ScriptedDice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Pins the round's transitions and settlement on handcrafted positions — no entropy anywhere but the deal test,
 * where the {@link ScriptedDice} identity shuffle makes the canonical deck order (clubs TWO..ACE first) the
 * script. Everything after a deal is a pure value computation: that determinism is the capture-at-offer payoff
 * this vertical exists to demonstrate.
 */
class BlackjackRoundTest {

    private static Card card(Rank rank) {
        return new Card(rank, Suit.SPADES);
    }

    private static Hand hand(Rank... ranks) {
        return new Hand(List.of(ranks).stream().map(BlackjackRoundTest::card).toList());
    }

    private static Deck deckOf(Rank... ranks) {
        return new Deck(List.of(ranks).stream().map(rank -> new Card(rank, Suit.HEARTS)).toList());
    }

    @Test
    void deal_gives_two_to_the_player_then_two_to_the_dealer_off_the_shuffled_deck() {
        BlackjackRound round = BlackjackRound.deal(new ScriptedDice());
        // Identity shuffle → canonical order: TWO♣ THREE♣ to the player, FOUR♣ (up) FIVE♣ (hole) to the dealer.
        assertThat(round.getPlayerHand()).isEqualTo(new Hand(List.of(
                new Card(Rank.TWO, Suit.CLUBS), new Card(Rank.THREE, Suit.CLUBS))));
        assertThat(round.dealerUpcard()).isEqualTo(new Card(Rank.FOUR, Suit.CLUBS));
        assertThat(round.getDealerHand().getCards()).containsExactly(
                new Card(Rank.FOUR, Suit.CLUBS), new Card(Rank.FIVE, Suit.CLUBS));
        assertThat(round.getDeck().size()).isEqualTo(48);
    }

    @Test
    void player_draws_takes_the_top_card_copy_on_write() {
        BlackjackRound round = new BlackjackRound(hand(Rank.TWO, Rank.THREE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.KING, Rank.NINE));
        BlackjackRound drawn = round.playerDraws();
        assertThat(drawn.getPlayerHand().bestValue()).isEqualTo(15);
        assertThat(drawn.getDeck().size()).isEqualTo(1);
        assertThat(round.getPlayerHand().getCards()).hasSize(2);
    }

    @Test
    void a_busted_round_takes_no_more_draws() {
        BlackjackRound bust = new BlackjackRound(hand(Rank.KING, Rank.QUEEN, Rank.FIVE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.NINE));
        assertThatIllegalStateException().isThrownBy(bust::playerDraws);
    }

    @Test
    void dealer_plays_out_drawing_to_sixteen_and_standing_on_seventeen() {
        // Dealer holds hard 16 → must draw the ACE (soft 17) → stands on all seventeens.
        BlackjackRound round = new BlackjackRound(hand(Rank.TEN, Rank.NINE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.ACE, Rank.KING));
        BlackjackRound played = round.dealerPlaysOut();
        assertThat(played.getDealerHand().bestValue()).isEqualTo(17);
        assertThat(played.getDealerHand().getCards()).hasSize(3);
        assertThat(played.getDeck().size()).isEqualTo(1);
    }

    @Test
    void dealer_already_on_seventeen_stands_pat_and_draws_nothing() {
        BlackjackRound round = new BlackjackRound(hand(Rank.TEN, Rank.NINE), hand(Rank.TEN, Rank.SEVEN),
                deckOf(Rank.ACE));
        assertThat(round.dealerPlaysOut()).isEqualTo(round);
    }

    @Test
    void settle_reads_the_position_as_it_lies() {
        Deck deck = deckOf(Rank.TWO);
        assertThat(new BlackjackRound(hand(Rank.KING, Rank.QUEEN, Rank.FIVE), hand(Rank.TEN, Rank.SEVEN), deck)
                .settle()).isEqualTo(RoundOutcome.PLAYER_BUSTED);
        assertThat(new BlackjackRound(hand(Rank.TEN, Rank.NINE), hand(Rank.TEN, Rank.SIX, Rank.KING), deck)
                .settle()).isEqualTo(RoundOutcome.DEALER_BUSTED);
        assertThat(new BlackjackRound(hand(Rank.TEN, Rank.NINE), hand(Rank.TEN, Rank.EIGHT), deck)
                .settle()).isEqualTo(RoundOutcome.PLAYER_WINS);
        assertThat(new BlackjackRound(hand(Rank.TEN, Rank.SEVEN), hand(Rank.TEN, Rank.EIGHT), deck)
                .settle()).isEqualTo(RoundOutcome.DEALER_WINS);
        assertThat(new BlackjackRound(hand(Rank.TEN, Rank.EIGHT), hand(Rank.TEN, Rank.EIGHT), deck)
                .settle()).isEqualTo(RoundOutcome.PUSH);
    }

    @Test
    void a_player_natural_settles_against_the_dealers_dealt_hand() {
        Deck deck = deckOf(Rank.TWO);
        BlackjackRound natural = new BlackjackRound(hand(Rank.ACE, Rank.KING), hand(Rank.TEN, Rank.EIGHT), deck);
        assertThat(natural.isNaturalBlackjack()).isTrue();
        assertThat(natural.settle()).isEqualTo(RoundOutcome.PLAYER_WINS);
        BlackjackRound bothNatural = new BlackjackRound(hand(Rank.ACE, Rank.KING), hand(Rank.QUEEN, Rank.ACE), deck);
        assertThat(bothNatural.settle()).isEqualTo(RoundOutcome.PUSH);
    }

    @Test
    void construction_requires_dealt_hands_and_a_deck() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(
                () -> new BlackjackRound(hand(Rank.TWO), hand(Rank.TEN, Rank.SIX), deckOf(Rank.NINE)));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(
                () -> new BlackjackRound(hand(Rank.TWO, Rank.SIX), Hand.empty(), deckOf(Rank.NINE)));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(
                () -> new BlackjackRound(hand(Rank.TWO, Rank.SIX), hand(Rank.TEN, Rank.SIX), null));
    }
}
