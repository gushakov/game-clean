package com.github.gameclean.infrastructure.terminal.conversation;

import com.github.gameclean.infrastructure.terminal.Affordance;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import com.github.gameclean.infrastructure.terminal.command.Command;
import com.github.gameclean.infrastructure.terminal.command.SelectCommand;

/**
 * A multi-interaction terminal dialogue "dressed up" as an object: it knows which {@link AffordanceKind} of
 * armed affordance it owns, which parsed lines <em>continue</em> it, and how to <em>resume</em> the dialogue
 * when one does. This is the delivery-mechanism (<em>modal input-routing</em>) half of a conversation — the
 * half that decides which use case a follow-up line invokes given a line-oriented channel. The <em>semantic</em>
 * half (the dialogue's steps and outcomes) lives entirely in the use case; nothing here crosses into the core
 * ({@link Command} is infra, and the handler only forwards values inward).
 *
 * <p><b>The container is the resumer map.</b> {@code ConsoleSession} injects every {@code Conversation} bean as
 * a {@code List} and matches the one whose {@link #kind()} equals the armed affordance's kind — so there is no
 * hand-maintained {@code kind→useCase} table duplicating the bean wiring; adding a conversation is adding a
 * bean.
 *
 * <p><b>{@link #continuedBy} is each dialogue's own continuation predicate</b> — the mode object owning "does
 * this line continue me?" that design-notes §4 reserved for the first conversation continuing on something
 * other than a bare number (blackjack, whose table talk continues on verbs). The dispatcher stays generic: the
 * armed conversation gets first crack at the parsed command; on refusal it is abandoned and the line falls
 * through to normal dispatch. The predicate quantifies over {@code Command} <em>types</em> (shape-matching,
 * never evaluation) — exactly the vocabulary the core excludes, which is why it lives here. The default suits
 * the numbered-menu family: a bare number ({@link SelectCommand}) continues.
 *
 * <p>Why infra and not a core type: the routing vocabulary it speaks ({@code Command}/{@code AffordanceKind}) is
 * meaningful only to a line-oriented terminal — it would not survive a second adapter (the async ticker drives
 * a use case with no {@code Command} at all). Putting {@code Conversation} in the core, even relocating
 * {@code Command} to satisfy ArchUnit, would pass the dependency check while defeating it (design-notes §1, §9).
 */
public interface Conversation {

    /** The kind of armed affordance this conversation owns — matched against the armed {@code AffordanceContext}. */
    AffordanceKind kind();

    /**
     * Whether the given parsed line continues this dialogue while it is armed — pure shape-matching over the
     * command's type, never over domain state or values. A refused line abandons the dialogue (the dispatcher
     * clears the affordance) and is dispatched normally.
     *
     * @param command the parsed line
     * @return {@code true} when the line should resume this conversation
     */
    default boolean continuedBy(Command command) {
        return command instanceof SelectCommand;
    }

    /**
     * Resumes this dialogue: the player's line continues the armed affordance. Invokes the conversation's
     * matching interaction on a freshly pulled prototype use case, relaying the affordance's content in as
     * values — the tokens for a selection dialogue, the opaque state envelope for an ephemeral one.
     *
     * @param command    the parsed continuing line (already accepted by {@link #continuedBy})
     * @param affordance the armed affordance, whole, as a value
     */
    void resume(Command command, Affordance affordance);
}
