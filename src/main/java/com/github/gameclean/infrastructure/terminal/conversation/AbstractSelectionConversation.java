package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.infrastructure.terminal.command.Command;
import com.github.gameclean.infrastructure.terminal.command.SelectCommand;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * Template-Method base for the selection conversations: it factors the part every numbered-menu dialogue shares
 * — casting the answer {@link Command} to a {@link SelectCommand} and extracting its ordinal — leaving each
 * concrete to supply only its {@link #kind()} and which completion interaction the ordinal drives
 * ({@link #resumeWith(int, List)}). It mirrors {@code AbstractSelectTargetSubcase} (the core-side Template
 * Method) and, like it, emerges at the <em>second</em> instance — {@code examine} (first) and {@code take}
 * (second) both continue on a bare number, so the shared cast earns its base now.
 *
 * <p>The split is also a testability seam: the cast/ordinal extraction here is unit-testable in isolation,
 * while each concrete's {@code getBean}+call is exercised by integration tests, like every prototype-pull site.
 * Concretes hold the {@link ApplicationContext} to pull a <em>fresh</em> prototype use case per resume (the
 * established idiom — a captured prototype would silently defeat the scope).
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PROTECTED)
public abstract class AbstractSelectionConversation implements Conversation {

    ApplicationContext applicationContext;

    @Override
    public void resume(Command command, List<String> offer) {
        int ordinal = ((SelectCommand) command).getOrdinal();
        resumeWith(ordinal, offer);
    }

    /**
     * Drives this conversation's completion interaction with the player's pick and the remembered offer — the
     * one point of variation between selection conversations.
     *
     * @param ordinal the 1-based menu number the player picked
     * @param offer   the candidate tokens last offered, in display order (handed in as a value)
     */
    protected abstract void resumeWith(int ordinal, List<String> offer);
}
