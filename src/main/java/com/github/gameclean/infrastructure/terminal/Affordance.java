package com.github.gameclean.infrastructure.terminal;

/**
 * One armed affordance — the immutable snapshot of what the system last offered the player, as held by the
 * {@link AffordanceContext} and handed whole to the resuming {@code Conversation}. A <b>sealed</b> type with
 * one carrier per conversation family, so the two payload disciplines are structural, not a convention over
 * one always-half-empty record:
 *
 * <ul>
 *   <li>{@link SelectionAffordance} — the selection conversations' numbered-menu offer: raw id
 *       <b>correlation tokens</b> in display order, relay-only, resolved and re-validated by the resuming use
 *       case against live state. The minimal currency, because a durable source of truth exists to correlate
 *       against.</li>
 *   <li>{@link EphemeralAffordance} — an <em>ephemeral</em> conversation's entire between-interaction state
 *       (a blackjack round) parked as an <b>opaque envelope</b>, because the domain deliberately remembers
 *       nothing. System-authored and valid by provenance, so no construction gate applies on the way back in —
 *       but it is <b>opaque to the shell</b>: this type, the context, and the dispatcher never read into it;
 *       only the conversation handler narrows it back to its concrete type (a cast, the {@code SelectCommand}
 *       precedent) and only the use case interprets it. "Primitives inward" governs <em>player-authored</em>
 *       input; the envelope is the system's own state in temporary shell custody. The opacity is what keeps a
 *       rich payload from ever becoming business logic in the adapter.</li>
 * </ul>
 *
 * <p>The XOR — a token offer <em>xor</em> an opaque envelope, never both, never neither — is enforced by the
 * type: each carrier holds exactly the field its family needs, so an exhaustive {@code switch}/{@code
 * instanceof} narrowing makes the next family unforgettable (the sealed-{@code Location} lesson,
 * design-notes §2). {@link #getKind()} is the orthogonal routing key ({@link AffordanceKind}) both carriers
 * share; it selects the resuming {@code Conversation}, independent of which family the affordance is.
 */
public sealed interface Affordance permits SelectionAffordance, EphemeralAffordance {

    /**
     * @return the kind of conversation that armed this affordance — the routing key the console matches against
     *         the {@code Conversation} handlers to resume a continuation.
     */
    AffordanceKind getKind();
}
