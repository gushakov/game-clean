package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.core.model.blackjack.BlackjackRound;
import com.github.gameclean.core.model.blackjack.Card;
import com.github.gameclean.core.model.blackjack.Deck;
import com.github.gameclean.core.model.blackjack.Hand;
import com.github.gameclean.core.model.blackjack.Rank;
import com.github.gameclean.core.model.blackjack.Suit;
import com.github.gameclean.core.usecase.blackjack.PlayBlackjackInputPort;
import com.github.gameclean.infrastructure.terminal.Affordance;
import com.github.gameclean.infrastructure.terminal.SelectionKind;
import com.github.gameclean.infrastructure.terminal.command.GameStandingCommand;
import com.github.gameclean.infrastructure.terminal.command.HitCardCommand;
import com.github.gameclean.infrastructure.terminal.command.HitCommand;
import com.github.gameclean.infrastructure.terminal.command.PlayCommand;
import com.github.gameclean.infrastructure.terminal.command.SelectCommand;
import com.github.gameclean.infrastructure.terminal.command.StandCommand;
import com.github.gameclean.infrastructure.terminal.command.UnknownCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the blackjack mode object: its continuation grammar (pure shape-matching over command types)
 * and its resume routing — each accepted verb drives the matching interaction on a freshly pulled prototype
 * use case, relaying the affordance's opaque round envelope. The first verb-continued conversation, so unlike
 * the selection conversations it overrides {@code continuedBy}.
 */
@ExtendWith(MockitoExtension.class)
class BlackjackConversationTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private PlayBlackjackInputPort playBlackjackUseCase;

    @Test
    void owns_the_blackjack_kind() {
        assertThat(new BlackjackConversation(applicationContext).kind()).isEqualTo(SelectionKind.BLACKJACK);
    }

    @Test
    void is_continued_by_the_table_talk_verbs_and_nothing_else() {
        BlackjackConversation conversation = new BlackjackConversation(applicationContext);

        assertThat(conversation.continuedBy(new HitCardCommand())).isTrue();
        assertThat(conversation.continuedBy(new StandCommand())).isTrue();
        assertThat(conversation.continuedBy(new GameStandingCommand())).isTrue();
        assertThat(conversation.continuedBy(new PlayCommand())).isTrue();

        // Notably refused: a bare number (this is not a menu), combat's targeted hit (attacking mid-hand
        // abandons the table), and anything unrecognized.
        assertThat(conversation.continuedBy(new SelectCommand(2))).isFalse();
        assertThat(conversation.continuedBy(new HitCommand("goblin"))).isFalse();
        assertThat(conversation.continuedBy(new UnknownCommand("north"))).isFalse();
    }

    @Test
    void resumes_each_verb_onto_its_interaction_relaying_the_round_envelope() {
        when(applicationContext.getBean(PlayBlackjackInputPort.class)).thenReturn(playBlackjackUseCase);
        BlackjackConversation conversation = new BlackjackConversation(applicationContext);
        BlackjackRound round = liveRound();
        Affordance armed = new Affordance(SelectionKind.BLACKJACK, List.of(), round);

        conversation.resume(new HitCardCommand(), armed);
        verify(playBlackjackUseCase).playerAsksDealerForHitCard(round);

        conversation.resume(new StandCommand(), armed);
        verify(playBlackjackUseCase).playerRequestsToStand(round);

        conversation.resume(new GameStandingCommand(), armed);
        verify(playBlackjackUseCase).playerExaminesGame(round);
    }

    @Test
    void a_mid_hand_play_re_presents_the_table_instead_of_dealing_again() {
        when(applicationContext.getBean(PlayBlackjackInputPort.class)).thenReturn(playBlackjackUseCase);
        BlackjackConversation conversation = new BlackjackConversation(applicationContext);
        BlackjackRound round = liveRound();

        conversation.resume(new PlayCommand(), new Affordance(SelectionKind.BLACKJACK, List.of(), round));

        verify(playBlackjackUseCase).playerExaminesGame(round);
    }

    private static BlackjackRound liveRound() {
        return new BlackjackRound(
                new Hand(Stream.of(Rank.TWO, Rank.THREE).map(r -> new Card(r, Suit.SPADES)).toList()),
                new Hand(Stream.of(Rank.TEN, Rank.SIX).map(r -> new Card(r, Suit.HEARTS)).toList()),
                new Deck(List.of(new Card(Rank.KING, Suit.CLUBS))));
    }
}
