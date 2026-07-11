package com.github.gameclean.infrastructure.terminal.render;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renders the item-interaction outcomes — {@code examine}'s reveal, the shared disambiguation dialogue, and
 * {@code take}/{@code drop}'s confirmations and misses — to the shared console. The item counterpart of
 * {@link CurrentSceneRenderer}: a shared, domain-aware collaborator (it knows {@link Item}) over the
 * domain-agnostic {@link Console}. Where a provenance-neutral select outcome reads differently by context
 * (nothing designated; the chosen one gone), it offers a ground-flavored and a carry-flavored rendering and
 * each presenter picks its own English.
 *
 * <p>It only <em>renders</em> the numbered menu it is given; it does not decide the order or remember the
 * offer. The presenter imposes a stable order, passes it here to display (numbered 1..N), and separately
 * deposits the same order as the affordance — see
 * {@link com.github.gameclean.infrastructure.terminal.presenter.TerminalExaminePresenter}.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ItemRenderer {

    Console console;

    /** The reveal: the item's short description as a heading, then its full description beneath. */
    public void renderItemDescription(Item item) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
                .append(item.getShortDescription())
                .style(AttributedStyle.DEFAULT)
                .append(System.lineSeparator())
                .append(item.getFullDescription().strip());
        console.write(sb);
    }

    /** Nothing present is designated by the fragment the player typed. */
    public void renderNoSuchTarget(String target) {
        console.printError("You see nothing like '%s' here.".formatted(target));
    }

    /** Nothing the player carries is designated by the fragment — the carry-flavored twin of {@link #renderNoSuchTarget}. */
    public void renderNoSuchCarriedTarget(String target) {
        console.printError("You are not carrying anything like '%s'.".formatted(target));
    }

    /**
     * The disambiguation menu: a prompt naming the ambiguous fragment, then the candidates numbered 1..N in
     * the order given (the presenter's stable order), then how to choose.
     */
    public void renderAmbiguousTarget(String target, List<Item> orderedCandidates) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("Which '%s' do you mean?".formatted(target))
                .style(AttributedStyle.DEFAULT);
        int number = 1;
        for (Item candidate : orderedCandidates) {
            sb.append(System.lineSeparator())
                    .append("  %d. ".formatted(number++))
                    .append(candidate.getShortDescription());
        }
        sb.append(System.lineSeparator()).append("Type the number to choose.");
        console.write(sb);
    }

    /** A chosen item that is no longer on the ground here (taken, moved, or despawned since it was offered). */
    public void renderItemNoLongerHere(ItemId itemId) {
        console.printError("That is no longer here.");
    }

    /** A chosen item that is no longer in the player's keeping — the carry-flavored twin of {@link #renderItemNoLongerHere}. */
    public void renderItemNoLongerCarried(ItemId itemId) {
        console.printError("You are no longer carrying that.");
    }

    /** Confirmation that the player has taken an item into their keeping. */
    public void renderItemTaken(Item item) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                .append("You take %s".formatted(item.getShortDescription()));
        console.write(sb);
    }

    /**
     * The take lost a concurrent race — the item was there when chosen but another actor took it first, so this
     * take was rejected. The write-side twin of {@link #renderItemNoLongerHere(ItemId)}; reads the same to the
     * player.
     */
    public void renderItemGotAway(ItemId itemId) {
        console.printError("Someone got there first — it is no longer here.");
    }

    /** Confirmation that the player has put an item down in the current scene. */
    public void renderItemDropped(Item item) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                .append("You drop %s".formatted(item.getShortDescription()));
        console.write(sb);
    }

    /** The player picked a number outside the offered menu; the menu still stands. */
    public void renderNoSuchOption(int ordinal) {
        console.printError("There is no option %d. Type one of the numbers shown.".formatted(ordinal));
    }
}
