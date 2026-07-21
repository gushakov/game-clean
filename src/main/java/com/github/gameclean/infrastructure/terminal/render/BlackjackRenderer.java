package com.github.gameclean.infrastructure.terminal.render;

import com.github.gameclean.core.model.blackjack.BlackjackRound;
import com.github.gameclean.core.model.blackjack.Card;
import com.github.gameclean.core.model.blackjack.Hand;
import com.github.gameclean.core.model.blackjack.RoundOutcome;
import com.github.gameclean.core.model.scene.Scene;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Renders the blackjack table talk — the deals, the standing of the table, and the settlements. The card-game
 * counterpart of {@link NpcRenderer} and {@link ItemRenderer}: a shared, domain-aware collaborator (it knows
 * {@link Hand} and {@link BlackjackRound}) over the domain-agnostic {@link Console}. All output is the
 * synchronous response to a player command, so it uses {@link Console#write}/{@link Console#printError}.
 *
 * <p>Cards render in card-table notation — rank symbol plus suit glyph ({@code 10♥}, {@code A♠}) — with the
 * suit's colour: hearts and diamonds red, spades and clubs <em>bright white</em> (the terminal background is
 * black, so the "black" suits must never be rendered black). The suit glyphs (U+2660–U+2666) are original IBM
 * PC codepage characters, present even in the legacy DOS codepages, so they are as terminal-safe as ASCII.
 * The live-hand views show only what the player may see (their hand, the dealer's upcard); the settlement
 * views turn everything face up. Each live view ends with the visible face of the armed affordance — what the
 * player can say next — mirroring the numbered menu's "Type the number to choose."
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class BlackjackRenderer {

    private static final AttributedStyle TABLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle WIN = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold();
    private static final AttributedStyle LOSS = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
    private static final AttributedStyle PUSH = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    /** Hearts and diamonds. */
    private static final AttributedStyle RED_SUIT = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();
    /** Spades and clubs — bright white, never black-on-black. */
    private static final AttributedStyle BLACK_SUIT = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold();

    Console console;

    /** The sit-down refused: this scene's authored offering includes no blackjack. */
    public void renderNoOneDealsCardsHere(Scene scene) {
        console.printError("No one deals cards here.");
    }

    /** The opening deal of a live hand: the player's cards, the dealer's upcard, and what can be said next. */
    public void renderInitialDeal(Hand playerHand, Card upcard) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(TABLE).append("The dealer deals.").style(AttributedStyle.DEFAULT);
        appendHandLine(sb, playerHand);
        appendUpcardLine(sb, upcard);
        appendSayNext(sb);
        console.write(sb);
    }

    /** A hit card left the hand live: the drawn card, the grown hand, the unchanged upcard. */
    public void renderCardDealt(Hand playerHand, Card upcard) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(TABLE).append("The dealer slides you ").style(AttributedStyle.DEFAULT);
        appendCard(sb, lastCardOf(playerHand));
        sb.style(TABLE).append(".").style(AttributedStyle.DEFAULT);
        appendHandLine(sb, playerHand);
        appendUpcardLine(sb, upcard);
        appendSayNext(sb);
        console.write(sb);
    }

    /** The anytime table view: where the game stands, nothing changed. */
    public void renderGameStanding(Hand playerHand, Card upcard) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(TABLE).append("On the table:").style(AttributedStyle.DEFAULT);
        appendHandLine(sb, playerHand);
        appendUpcardLine(sb, upcard);
        appendSayNext(sb);
        console.write(sb);
    }

    /** A natural blackjack at the deal — everything face up, won (or pushed against a dealer twenty-one). */
    public void renderNaturalBlackjack(BlackjackRound round, RoundOutcome outcome) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(WIN).append("Blackjack!").style(AttributedStyle.DEFAULT);
        appendHandLine(sb, round.getPlayerHand());
        appendDealerHandLine(sb, round.getDealerHand());
        sb.append(System.lineSeparator());
        if (outcome == RoundOutcome.PUSH) {
            sb.style(PUSH).append("The dealer has twenty-one too. Push: nobody wins.");
        } else {
            sb.style(WIN).append("You win.");
        }
        console.write(sb);
    }

    /** The hit card busted the player — the hand is over. */
    public void renderPlayerBusted(BlackjackRound round) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("Your hand: ");
        appendCards(sb, round.getPlayerHand());
        sb.append(" (%d).".formatted(round.getPlayerHand().bestValue()))
                .append(System.lineSeparator())
                .style(LOSS).append("Bust at %d. The dealer sweeps the cards.".formatted(
                        round.getPlayerHand().bestValue()));
        console.write(sb);
    }

    /** The dealer's playout busted — the player wins. */
    public void renderDealerBusted(BlackjackRound round) {
        AttributedStringBuilder sb = settlementOpening(round);
        sb.append(System.lineSeparator())
                .style(WIN).append("The dealer busts at %d. You win.".formatted(
                        round.getDealerHand().bestValue()));
        console.write(sb);
    }

    /** Both stood under twenty-two and the player's hand is higher. */
    public void renderPlayerWins(BlackjackRound round) {
        AttributedStringBuilder sb = settlementOpening(round);
        sb.append(System.lineSeparator())
                .style(WIN).append("%d against %d: you win.".formatted(
                        round.getPlayerHand().bestValue(), round.getDealerHand().bestValue()));
        console.write(sb);
    }

    /** Both stood under twenty-two and the dealer's hand is higher. */
    public void renderDealerWins(BlackjackRound round) {
        AttributedStringBuilder sb = settlementOpening(round);
        sb.append(System.lineSeparator())
                .style(LOSS).append("%d against your %d: the dealer wins.".formatted(
                        round.getDealerHand().bestValue(), round.getPlayerHand().bestValue()));
        console.write(sb);
    }

    /** Equal values — nobody wins. */
    public void renderPush(BlackjackRound round) {
        AttributedStringBuilder sb = settlementOpening(round);
        sb.append(System.lineSeparator())
                .style(PUSH).append("%d apiece. Push: nobody wins.".formatted(
                        round.getPlayerHand().bestValue()));
        console.write(sb);
    }

    /** The settlement's shared opening: the dealer turns the hole card and plays out, everything face up. */
    private static AttributedStringBuilder settlementOpening(BlackjackRound round) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(TABLE).append("The dealer turns the hole card and plays out.").style(AttributedStyle.DEFAULT);
        appendHandLine(sb, round.getPlayerHand());
        appendDealerHandLine(sb, round.getDealerHand());
        return sb;
    }

    private static void appendHandLine(AttributedStringBuilder sb, Hand playerHand) {
        sb.append(System.lineSeparator()).append("Your hand: ");
        appendCards(sb, playerHand);
        sb.append(" (%d).".formatted(playerHand.bestValue()));
    }

    private static void appendDealerHandLine(AttributedStringBuilder sb, Hand dealerHand) {
        sb.append(System.lineSeparator()).append("Dealer's hand: ");
        appendCards(sb, dealerHand);
        sb.append(" (%d).".formatted(dealerHand.bestValue()));
    }

    private static void appendUpcardLine(AttributedStringBuilder sb, Card upcard) {
        sb.append(System.lineSeparator()).append("The dealer shows ");
        appendCard(sb, upcard);
        sb.append(".");
    }

    private static void appendSayNext(AttributedStringBuilder sb) {
        sb.append(System.lineSeparator()).append("Say 'hit me' or 'stand'. ('game' shows the table.)");
    }

    private static void appendCards(AttributedStringBuilder sb, Hand hand) {
        boolean first = true;
        for (Card card : hand.getCards()) {
            if (!first) {
                sb.append(" ");
            }
            appendCard(sb, card);
            first = false;
        }
    }

    /** One card in table notation, in its suit's colour: {@code 10♥} red, {@code A♠} bright white. */
    private static void appendCard(AttributedStringBuilder sb, Card card) {
        sb.style(suitStyle(card)).append(rankSymbol(card) + suitGlyph(card)).style(AttributedStyle.DEFAULT);
    }

    private static AttributedStyle suitStyle(Card card) {
        return switch (card.getSuit()) {
            case HEARTS, DIAMONDS -> RED_SUIT;
            case SPADES, CLUBS -> BLACK_SUIT;
        };
    }

    private static String rankSymbol(Card card) {
        return switch (card.getRank()) {
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            case TEN -> "10";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            case ACE -> "A";
        };
    }

    private static String suitGlyph(Card card) {
        return switch (card.getSuit()) {
            case CLUBS -> "♣";    // U+2663
            case DIAMONDS -> "♦"; // U+2666
            case HEARTS -> "♥";   // U+2665
            case SPADES -> "♠";   // U+2660
        };
    }

    private static Card lastCardOf(Hand hand) {
        return hand.getCards().get(hand.getCards().size() - 1);
    }
}
