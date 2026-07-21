package com.github.gameclean.infrastructure.terminal;

import lombok.Value;

import java.util.List;
import java.util.Objects;

/**
 * The selection conversations' armed affordance: a numbered-menu offer carried as raw id <b>correlation
 * tokens</b> in the order the presenter displayed (and numbered) them. Relay-only — written once by the driven
 * presenter, read once by the resuming {@code Conversation}, forwarded straight into the input port, then
 * re-validated by the use case against live state — so the shell never resolves or decides anything from it.
 * Kept a {@code List<String>} of primitives on purpose: "primitives inward" at the driving edge (design-notes
 * §9), and the minimal currency because a durable source of truth exists to correlate the pick against.
 *
 * @see Affordance
 */
@Value
public class SelectionAffordance implements Affordance {

    AffordanceKind kind;

    /** The offered candidate id tokens in display order. */
    List<String> tokens;

    public SelectionAffordance(AffordanceKind kind, List<String> tokens) {
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens must not be null"));
    }
}
