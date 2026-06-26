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
 * Renders the {@code examine}-specific outcomes — an item's full description, "nothing like that here", the
 * disambiguation menu, and "no longer here" — to the shared console. The {@code examine} counterpart of
 * {@link CurrentSceneRenderer}: a shared, domain-aware collaborator (it knows {@link Item}) over the
 * domain-agnostic {@link Console}.
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

    /** The player typed a number with no disambiguation menu pending. */
    public void renderNoPendingSelection() {
        console.printError("There is nothing to choose right now. Try 'look <target>' to inspect something.");
    }

    /** The player picked a number outside the offered menu; the menu still stands. */
    public void renderNoSuchOption(int ordinal) {
        console.printError("There is no option %d. Type one of the numbers shown.".formatted(ordinal));
    }
}
