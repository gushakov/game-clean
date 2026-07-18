package com.github.gameclean.core.model.blackjack;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.dice.SeededDice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DeckTest {

    @Test
    void a_freshly_shuffled_deck_holds_all_fifty_two_distinct_cards() {
        Deck deck = Deck.freshlyShuffled(new SeededDice(42));
        assertThat(deck.size()).isEqualTo(52);
        assertThat(deck.getCards()).doesNotHaveDuplicates();
    }

    @Test
    void the_scripted_dice_identity_shuffle_yields_the_canonical_order() {
        Deck deck = Deck.freshlyShuffled(new ScriptedDice());
        assertThat(deck.top()).isEqualTo(new Card(Rank.TWO, Suit.CLUBS));
    }

    @Test
    void drawing_reads_the_top_and_shortens_the_deck_copy_on_write() {
        Deck deck = new Deck(List.of(new Card(Rank.ACE, Suit.HEARTS), new Card(Rank.KING, Suit.SPADES)));
        assertThat(deck.top()).isEqualTo(new Card(Rank.ACE, Suit.HEARTS));
        Deck drawn = deck.withoutTop();
        assertThat(drawn.top()).isEqualTo(new Card(Rank.KING, Suit.SPADES));
        assertThat(deck.size()).isEqualTo(2);
    }

    @Test
    void drawing_from_an_empty_deck_is_a_caller_bug() {
        Deck empty = new Deck(List.of());
        assertThatIllegalStateException().isThrownBy(empty::top);
        assertThatIllegalStateException().isThrownBy(empty::withoutTop);
    }

    @Test
    void construction_rejects_a_null_card_list() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new Deck(null));
    }
}
