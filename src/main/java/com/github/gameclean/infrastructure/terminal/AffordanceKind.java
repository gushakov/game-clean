package com.github.gameclean.infrastructure.terminal;

/**
 * Which conversation the armed affordance belongs to — the routing key the {@link Affordance} carries, so a
 * continuing line resumes the dialogue that armed it rather than whichever one happened to arm last. An
 * infrastructure-local terminal concern: it names <em>delivery-mechanism</em> conversations ({@code examine},
 * {@code take}, the blackjack table talk), not domain concepts, and never crosses into the core.
 *
 * <p>Spans both affordance families: the four disambiguation kinds are {@link SelectionAffordance}s, {@code
 * BLACKJACK} is an {@link EphemeralAffordance} — which is why the enum is named for the affordance, not for
 * "selection" (an earlier name that predated blackjack and mislabelled it). The <em>family</em> is the sealed
 * {@link Affordance} subtype; this enum is the orthogonal routing key.
 *
 * <p>Each driven presenter writes its own constant when it arms (a compile-time choice, so the console owns no
 * {@code kind→useCase} table); the matching
 * {@link com.github.gameclean.infrastructure.terminal.conversation.Conversation} handler declares the same
 * constant via {@code kind()}, and the container's collection of handlers <em>is</em> the resumer map. A
 * wiring-time check asserts every constant here has a handler.
 */
public enum AffordanceKind {

    /** A pending {@code examine} disambiguation: the next pick describes the chosen item. */
    EXAMINE,

    /** A pending {@code take} disambiguation: the next pick takes the chosen item. */
    TAKE,

    /** A pending {@code drop} disambiguation: the next pick drops the chosen item. */
    DROP,

    /** A pending {@code hit} disambiguation: the next pick strikes the chosen NPC. */
    HIT,

    /** A live blackjack hand: {@code hit}/{@code stand}/{@code game}/{@code play} continue the table talk. */
    BLACKJACK
}
