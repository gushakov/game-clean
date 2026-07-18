package com.github.gameclean.core.usecase.blackjack;

import com.github.gameclean.core.model.blackjack.BlackjackRound;
import com.github.gameclean.core.model.blackjack.Card;
import com.github.gameclean.core.model.blackjack.Deck;
import com.github.gameclean.core.model.blackjack.Hand;
import com.github.gameclean.core.model.blackjack.Rank;
import com.github.gameclean.core.model.blackjack.RoundOutcome;
import com.github.gameclean.core.model.blackjack.Suit;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.MiniGame;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link PlayBlackjackUseCase} in isolation — the presenter and the orient subcase are
 * mocked, and the use case is exercised directly through its input port. The whole conversation is read-free
 * and write-free, so there are no repository or transaction mocks at all: after the deal every test is a pure
 * value-in / presenter-capture-out check on handcrafted positions. The one stochastic interaction (the deal)
 * is pinned by the {@link ScriptedDice}, whose identity shuffle makes the canonical deck order the script and
 * whose {@code willShuffleToFront} rigs a natural.
 */
@ExtendWith(MockitoExtension.class)
class PlayBlackjackUseCaseTest {

    @Mock
    private PlayBlackjackPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Spy
    private ScriptedDice dice = new ScriptedDice();

    @InjectMocks
    private PlayBlackjackUseCase useCase;

    // --- sitting down: grounding, the deal, and the natural extension ----------------------------

    @Test
    void sittingDownDealsOffTheShuffledDeckPresentingTheInitialDealWithTheRoundToArm() {
        givenBearingsInASceneOffering(MiniGame.BLACKJACK);

        useCase.playerSitsDownToPlay();

        // Identity shuffle → canonical order: TWO♣ THREE♣ to the player, FOUR♣ up, FIVE♣ in the hole.
        Hand dealtPlayerHand = new Hand(List.of(
                new Card(Rank.TWO, Suit.CLUBS), new Card(Rank.THREE, Suit.CLUBS)));
        ArgumentCaptor<BlackjackRound> armed = ArgumentCaptor.forClass(BlackjackRound.class);
        verify(presenter).presentInitialDeal(
                eq(dealtPlayerHand), eq(new Card(Rank.FOUR, Suit.CLUBS)), armed.capture());
        assertThat(armed.getValue().getPlayerHand()).isEqualTo(dealtPlayerHand);
        assertThat(armed.getValue().getDeck().size()).isEqualTo(48);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void sittingDownWhereNoCardsAreDealtPresentsTheRefusal() {
        Scene scene = scene(Set.of());
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player(), scene));

        useCase.playerSitsDownToPlay();

        verify(presenter).presentNoOneDealsCardsHere(scene);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void aNaturalBlackjackSettlesAtTheDealAsAWin() {
        givenBearingsInASceneOffering(MiniGame.BLACKJACK);
        dice.willShuffleToFront(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES));

        useCase.playerSitsDownToPlay();

        ArgumentCaptor<BlackjackRound> settled = ArgumentCaptor.forClass(BlackjackRound.class);
        verify(presenter).presentNaturalBlackjack(settled.capture(), eq(RoundOutcome.PLAYER_WINS));
        assertThat(settled.getValue().isNaturalBlackjack()).isTrue();
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void mutualNaturalsSettleAtTheDealAsAPush() {
        givenBearingsInASceneOffering(MiniGame.BLACKJACK);
        dice.willShuffleToFront(
                new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES),
                new Card(Rank.ACE, Suit.HEARTS), new Card(Rank.QUEEN, Suit.HEARTS));

        useCase.playerSitsDownToPlay();

        verify(presenter).presentNaturalBlackjack(any(BlackjackRound.class), eq(RoundOutcome.PUSH));
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void presentsNothingWhenTheOrientSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerSitsDownToPlay();

        verifyNoInteractions(presenter);
    }

    @Test
    void routesAnUnexpectedSitDownFailureToTheCatchAll() {
        RuntimeException boom = new RuntimeException("boom");
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(boom);

        useCase.playerSitsDownToPlay();

        verify(presenter).presentError(boom);
    }

    // --- hit me: a live draw, a bust, and the wiring preconditions -------------------------------

    @Test
    void aHitCardLeavingTheHandLiveIsPresentedWithTheNewRoundToRearm() {
        BlackjackRound round = round(hand(Rank.TWO, Rank.THREE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.KING, Rank.NINE));

        useCase.playerAsksDealerForHitCard(round);

        Hand grownHand = round.getPlayerHand().plus(new Card(Rank.KING, Suit.HEARTS)); // the deck's top
        ArgumentCaptor<BlackjackRound> rearmed = ArgumentCaptor.forClass(BlackjackRound.class);
        verify(presenter).presentCardDealt(eq(grownHand), eq(round.dealerUpcard()), rearmed.capture());
        assertThat(rearmed.getValue().getPlayerHand().bestValue()).isEqualTo(15);
        assertThat(rearmed.getValue().getDeck().size()).isEqualTo(1);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void aHitCardBustingThePlayerSettlesTheHand() {
        BlackjackRound round = round(hand(Rank.KING, Rank.QUEEN), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.FIVE));

        useCase.playerAsksDealerForHitCard(round);

        ArgumentCaptor<BlackjackRound> busted = ArgumentCaptor.forClass(BlackjackRound.class);
        verify(presenter).presentPlayerBusted(busted.capture());
        assertThat(busted.getValue().isPlayerBust()).isTrue();
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void aHitWithNoArmedRoundIsAWiringFaultRoutedToTheCatchAll() {
        useCase.playerAsksDealerForHitCard(null);

        verify(presenter).presentError(any(IllegalStateException.class));
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void aHitWithABustedRoundIsAWiringFaultRoutedToTheCatchAll() {
        BlackjackRound busted = round(hand(Rank.KING, Rank.QUEEN, Rank.FIVE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.NINE));

        useCase.playerAsksDealerForHitCard(busted);

        verify(presenter).presentError(any(IllegalStateException.class));
        verifyNoMoreInteractions(presenter);
    }

    // --- standing: the synchronous, entropy-free playout and its settlement stripes --------------

    @Test
    void standingPlaysOutTheDealerWhoBusts() {
        BlackjackRound round = round(hand(Rank.TEN, Rank.NINE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.KING));

        useCase.playerRequestsToStand(round);

        ArgumentCaptor<BlackjackRound> played = ArgumentCaptor.forClass(BlackjackRound.class);
        verify(presenter).presentDealerBusted(played.capture());
        assertThat(played.getValue().getDealerHand().bestValue()).isEqualTo(26);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void standingSettlesEachNonBustStripeFromThePositionAlone() {
        // Player 19 over dealer's standing 17 — the player wins.
        useCase.playerRequestsToStand(round(hand(Rank.TEN, Rank.NINE), hand(Rank.TEN, Rank.SEVEN),
                deckOf(Rank.TWO)));
        verify(presenter).presentPlayerWins(any(BlackjackRound.class));

        // Dealer 18 over player's 17 — the dealer wins.
        useCase.playerRequestsToStand(round(hand(Rank.TEN, Rank.SEVEN), hand(Rank.TEN, Rank.EIGHT),
                deckOf(Rank.TWO)));
        verify(presenter).presentDealerWins(any(BlackjackRound.class));

        // Eighteen apiece — push.
        useCase.playerRequestsToStand(round(hand(Rank.TEN, Rank.EIGHT), hand(Rank.TEN, Rank.EIGHT),
                deckOf(Rank.TWO)));
        verify(presenter).presentPush(any(BlackjackRound.class));

        verifyNoMoreInteractions(presenter);
    }

    @Test
    void standingConsumesNoEntropy() {
        // The deck was captured at the deal, so the playout draws nothing from the dice: the strict
        // ScriptedDice (which throws on any un-scripted pull) passing untouched is the capture-at-offer proof.
        useCase.playerRequestsToStand(round(hand(Rank.TEN, Rank.NINE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.ACE, Rank.KING)));

        verify(presenter).presentPlayerWins(any(BlackjackRound.class));
        verifyNoInteractions(dice);
    }

    @Test
    void standingWithNoArmedRoundIsAWiringFaultRoutedToTheCatchAll() {
        useCase.playerRequestsToStand(null);

        verify(presenter).presentError(any(IllegalStateException.class));
        verifyNoMoreInteractions(presenter);
    }

    // --- the anytime extension: examining the game -----------------------------------------------

    @Test
    void examiningTheGamePresentsTheStandingAndRearmsTheSameRound() {
        BlackjackRound round = round(hand(Rank.TWO, Rank.THREE), hand(Rank.TEN, Rank.SIX),
                deckOf(Rank.KING));

        useCase.playerExaminesGame(round);

        verify(presenter).presentGameStanding(round.getPlayerHand(), round.dealerUpcard(), round);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void examiningWithNoArmedRoundIsAWiringFaultRoutedToTheCatchAll() {
        useCase.playerExaminesGame(null);

        verify(presenter).presentError(any(IllegalStateException.class));
        verifyNoMoreInteractions(presenter);
    }

    // --- fixtures -------------------------------------------------------------------------------

    private void givenBearingsInASceneOffering(MiniGame... games) {
        when(orientPlayerSubcase.playerGetsBearings())
                .thenReturn(new OrientPlayerResult(player(), scene(Set.of(games))));
    }

    private static Player player() {
        return Player.builder().id(new PlayerId("plr1")).currentScene(new SceneId("scn3")).build();
    }

    private static Scene scene(Set<MiniGame> miniGames) {
        return Scene.builder()
                .id(new SceneId("scn3"))
                .name("Armoury")
                .shortDescription("A plundered armoury.")
                .fullDescription("Empty weapon racks line the walls; a dealer waits at an upturned crate.")
                .exits(List.of())
                .miniGames(miniGames)
                .build();
    }

    private static Hand hand(Rank... ranks) {
        return new Hand(Stream.of(ranks).map(rank -> new Card(rank, Suit.SPADES)).toList());
    }

    private static Deck deckOf(Rank... ranks) {
        return new Deck(Stream.of(ranks).map(rank -> new Card(rank, Suit.HEARTS)).toList());
    }

    private static BlackjackRound round(Hand playerHand, Hand dealerHand, Deck deck) {
        return new BlackjackRound(playerHand, dealerHand, deck);
    }
}
