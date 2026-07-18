package com.github.gameclean.infrastructure.terminal;

import lombok.Value;

import java.util.List;
import java.util.Objects;

/**
 * One armed affordance — the immutable snapshot of what the system last offered the player, as held by the
 * {@link AffordanceContext} and handed whole to the resuming {@code Conversation}. Two payload shapes share it,
 * one per conversation family:
 *
 * <ul>
 *   <li><b>Correlation tokens</b> ({@link #tokens}) — the selection conversations' numbered-menu offer: raw id
 *       tokens in display order, relay-only, resolved and re-validated by the resuming use case against live
 *       state. The minimal currency, because a durable source of truth exists to correlate against.</li>
 *   <li><b>An opaque state envelope</b> ({@link #payload}) — an <em>ephemeral</em> conversation's entire
 *       between-interaction state (a blackjack round), parked here because the domain deliberately remembers
 *       nothing. System-authored and valid by provenance, so no construction gate applies on the way back in —
 *       but it is <b>opaque to the shell</b>: this class, the context, and the dispatcher never read into it;
 *       only the conversation handler hands it onward (a cast, the {@code SelectCommand} precedent) and only
 *       the use case interprets it. "Primitives inward" governs <em>player-authored</em> input; the envelope
 *       is the system's own state in temporary shell custody. The opacity is what keeps a rich payload from
 *       ever becoming business logic in the adapter.</li>
 * </ul>
 */
@Value
public class Affordance {

    SelectionKind kind;

    /** The offered candidate id tokens in display order — empty for a payload-carrying affordance. */
    List<String> tokens;

    /** The opaque state envelope — {@code null} for a token-carrying affordance. Never read by the shell. */
    Object payload;

    public Affordance(SelectionKind kind, List<String> tokens, Object payload) {
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens must not be null"));
        this.payload = payload;
    }
}
