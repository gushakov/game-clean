package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.infrastructure.terminal.SelectionKind;
import com.github.gameclean.infrastructure.terminal.command.Command;

import java.util.List;

/**
 * A multi-interaction terminal dialogue "dressed up" as an object: it knows which {@link SelectionKind} of
 * pending offer it owns and how to <em>resume</em> that dialogue when the player answers. This is the
 * delivery-mechanism (<em>modal input-routing</em>) half of a conversation — the half that decides which use
 * case a follow-up line invokes given a line-oriented channel. The <em>semantic</em> half (the dialogue's
 * steps and outcomes) lives entirely in the use case; nothing here crosses into the core ({@link Command} is
 * infra, and the handler only forwards primitives inward).
 *
 * <p><b>The container is the resumer map.</b> {@code ConsoleSession} injects every {@code Conversation} bean as
 * a {@code List} and matches the one whose {@link #kind()} equals the armed offer's kind — so there is no
 * hand-maintained {@code kind→useCase} table duplicating the bean wiring; adding a conversation is adding a
 * bean.
 *
 * <p>Why infra and not a core type: the routing vocabulary it speaks ({@code Command}/{@code SelectionKind}) is
 * meaningful only to a line-oriented terminal — it would not survive a second adapter (the async ticker drives
 * a use case with no {@code Command} at all). Putting {@code Conversation} in the core, even relocating
 * {@code Command} to satisfy ArchUnit, would pass the dependency check while defeating it (design-notes §1, §9).
 */
public interface Conversation {

    /** The kind of pending offer this conversation owns — matched against the armed {@code AffordanceContext}. */
    SelectionKind kind();

    /**
     * Resumes this dialogue: the player has answered the pending offer. Invokes the conversation's completion
     * interaction on a freshly pulled prototype use case, handing the remembered offer in as a value.
     *
     * @param command the parsed answer (a {@code SelectCommand} carrying the menu pick)
     * @param offer   the candidate tokens last offered, in display order (the conversational state, as a value)
     */
    void resume(Command command, List<String> offer);
}
