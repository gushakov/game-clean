package com.github.gameclean.infrastructure.terminal;

/**
 * Which conversation a pending disambiguation offer belongs to — the discriminator the
 * {@link AffordanceContext} carries alongside the offered tokens, so a bare number resumes the dialogue that
 * armed it rather than whichever one happened to arm last. An infrastructure-local terminal concern: it names
 * <em>delivery-mechanism</em> conversations ({@code examine}, {@code take}), not domain concepts, and never
 * crosses into the core.
 *
 * <p>Each driven presenter writes its own constant when it offers a menu (a compile-time choice, so the console
 * owns no {@code kind→useCase} table); the matching
 * {@link com.github.gameclean.infrastructure.terminal.conversation.Conversation} handler declares the same
 * constant via {@code kind()}, and the container's collection of handlers <em>is</em> the resumer map. A
 * wiring-time check asserts every constant here has a handler.
 */
public enum SelectionKind {

    /** A pending {@code examine} disambiguation: the next pick describes the chosen item. */
    EXAMINE,

    /** A pending {@code take} disambiguation: the next pick takes the chosen item. */
    TAKE
}
