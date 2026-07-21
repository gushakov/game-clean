package com.github.gameclean.infrastructure.terminal;

import lombok.Value;

import java.util.Objects;

/**
 * An ephemeral conversation's armed affordance: the entire between-interaction state of a dialogue the domain
 * deliberately does not persist (a blackjack round), parked as an <b>opaque envelope</b>. System-authored and
 * valid by provenance, so no construction gate applies on the way back in — but <b>opaque to the shell</b>:
 * neither this type, the {@link AffordanceContext}, nor the dispatcher ever read into the {@link #payload};
 * only the owning {@code Conversation} handler narrows it back to its concrete type (the {@code SelectCommand}
 * cast's twin), and only the use case interprets it. The opacity — not payload minimality — is what keeps a
 * rich payload from ever becoming business logic in the adapter (design-notes §9).
 *
 * @see Affordance
 */
@Value
public class EphemeralAffordance implements Affordance {

    AffordanceKind kind;

    /** The opaque state envelope. Never read by the shell — narrowed only by the owning conversation handler. */
    Object payload;

    public EphemeralAffordance(AffordanceKind kind, Object payload) {
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }
}
