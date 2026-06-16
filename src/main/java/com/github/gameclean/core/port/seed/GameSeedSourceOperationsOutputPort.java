package com.github.gameclean.core.port.seed;

/**
 * Driven (output) port for the authored game seed — the use case drives it; an infrastructure adapter
 * (over the YAML parsing machinery) implements it. The {@code OutputPort} suffix marks the hexagonal
 * direction: {@code InitializeGame} is the caller, the infrastructure ring the implementor.
 *
 * <p><b>A deliberately unusual boundary shape.</b> Persistence (the exemplar driven port) returns
 * <em>valid</em> domain models — data the domain wrote is valid by provenance. This port is different:
 * it sources <em>authored, untrusted</em> data, so it returns the possibly-invalid {@link GameSeed}
 * carrier of primitives / {@code *Entry} DTOs, <em>not</em> a domain model. An always-valid model
 * cannot be the return type, because its constructor would throw before the use case's validity gate is
 * reached — and rejecting bad authored input <em>as a presented outcome</em> is the whole point. So the
 * boundary-currency rule "invalid-capable carrier inward, valid model outward" still holds; the
 * invalid-capable carrier simply arrives as this port's <em>return value</em> rather than as an argument
 * pushed in by a driving adapter. This is the "untrusted external source, pulled like persistence" case
 * the design notes (§3) parked, realized.
 *
 * <p>Pulling the seed (rather than having a driving adapter push it) is what lets the use case
 * <em>drive</em> the whole interaction: loading the authored world becomes the use case's first
 * checkpoint, not logic stranded in an adapter outside it.
 *
 * <p>Failures surface as the unchecked {@link GameSeedSourceOperationsError}, caught at the use case's
 * single outermost checkpoint and presented — uniform with a persistence fault, rather than failing
 * startup from inside an adapter.
 */
public interface GameSeedSourceOperationsOutputPort {

    /**
     * Loads and assembles the complete authored seed — the scenes, the configured starting scene id, and
     * the items to spawn — from its underlying source. The returned carrier may be <em>domain</em>-invalid
     * (a blank name, a dangling exit target, a zero chance denominator): validation is the use case's job,
     * not this port's.
     *
     * @return the assembled seed (never {@code null})
     * @throws GameSeedSourceOperationsError if the seed cannot be read or parsed
     */
    GameSeed loadGameSeed();
}
