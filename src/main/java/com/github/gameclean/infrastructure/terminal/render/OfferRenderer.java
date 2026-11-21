package com.github.gameclean.infrastructure.terminal.render;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.StandingOffer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renders commerce-facing output — the outcomes of a {@code buy}: the purchase confirmation, the coin
 * shortfall, and the ware-flavored disambiguation dialogue. The ware counterpart of {@link NpcRenderer}: a
 * shared, domain-aware collaborator (it knows {@link StandingOffer} and {@link Item}) over the domain-agnostic
 * {@link Console}. All of it is the <em>synchronous</em> response to a player command, so every line is a
 * plain {@link Console#write} or {@link Console#printError} — nothing is narrated above the prompt.
 *
 * <p>The success line names no verb: the same renderer answers {@code buy} and its synonym {@code rent}, so it
 * speaks only the transaction's facts — coins paid, vendor paid, ware received. A purchase is an exchange,
 * not a conquest: no strike-styled flourish, just the trade plainly stated.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OfferRenderer {

    private static final AttributedStyle BOUGHT = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    private static final AttributedStyle MENU = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);

    Console console;

    /**
     * The completed purchase: the coins paid to the vendor, the ware now in hand. The transaction's facts as
     * the use case presented them — the price from the offer, the ware from the delivered item, no re-read.
     */
    public void renderWareBought(StandingOffer ware, Item delivered) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(BOUGHT).append("You pay %s to %s and receive %s.".formatted(
                English.quantity(ware.getOffer().getPrice(), "coin"),
                bareName(ware.getVendor().getShortDescription()),
                bareName(delivered.getShortDescription())));
        console.write(sb);
    }

    /**
     * The purchase refused for want of coins: what the ware costs and how much money the player actually
     * carries — both numbers the use case counted, this only phrasing them.
     */
    public void renderNotEnoughCoins(StandingOffer ware, int coinsHeld) {
        console.printError("%s costs %s — you hold %s.".formatted(
                ware.getOffer().getName(),
                English.quantity(ware.getOffer().getPrice(), "coin"),
                English.quantity(coinsHeld, "coin")));
    }

    /** Nothing for sale here is designated by the fragment the player typed — the ware-flavored no-such-target. */
    public void renderNoSuchWare(String target) {
        console.printError("Nobody here offers '%s'.".formatted(target));
    }

    /**
     * The disambiguation menu: a prompt naming the ambiguous fragment, then the wares numbered 1..N in the
     * order given (the presenter's stable order), each line its name, price and the vendor behind it, then how
     * to choose. The ware twin of {@link NpcRenderer#renderAmbiguousTarget}.
     */
    public void renderAmbiguousWare(String target, List<StandingOffer> orderedCandidates) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(MENU).append("Which '%s' do you mean?".formatted(target)).style(AttributedStyle.DEFAULT);
        int number = 1;
        for (StandingOffer candidate : orderedCandidates) {
            sb.append(System.lineSeparator())
                    .append("  %d. ".formatted(number++))
                    .append("%s — %s, from %s".formatted(
                            candidate.getOffer().getName(),
                            English.quantity(candidate.getOffer().getPrice(), "coin"),
                            bareName(candidate.getVendor().getShortDescription())));
        }
        sb.append(System.lineSeparator()).append("Type the number to choose.");
        console.write(sb);
    }

    /** A chosen ware no longer on offer here (its vendor moved on or fell since the menu was shown). */
    public void renderWareNoLongerAvailable() {
        console.printError("That ware is no longer on offer here.");
    }

    /** The player picked a number outside the offered menu; the menu still stands. */
    public void renderNoSuchOption(int ordinal) {
        console.printError("There is no option %d. Type one of the numbers shown.".formatted(ordinal));
    }

    /**
     * Short descriptions are authored as standalone sentences ("A stout innkeeper."); strip the trailing
     * period so the name reads cleanly embedded in a sentence — the {@link NpcRenderer} twin.
     */
    private static String bareName(String shortDescription) {
        String who = shortDescription.strip();
        return who.endsWith(".") ? who.substring(0, who.length() - 1) : who;
    }
}
