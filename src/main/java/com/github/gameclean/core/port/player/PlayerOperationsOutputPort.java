package com.github.gameclean.core.port.player;

/**
 * Driven (output) port that answers one question for an interaction: <b>who is the acting player?</b>
 * The {@code OutputPort} suffix marks the hexagonal direction — the core is the caller, an
 * infrastructure adapter the implementor.
 *
 * <p>This is the game's analogue of the reference methodology's {@code SecurityOperationsOutputPort}:
 * in a business application the current actor's identity comes from authentication; in this
 * single-player game there is none, so it comes from configuration. Either way the port's role is the
 * same — tell the use case which actor initiated the interaction — and the core stays ignorant of
 * <em>how</em> that identity is determined (configured today; a per-interaction context once NPCs and
 * the clock act asynchronously on shared state). That is what lets {@code playerLooksAround()} (and,
 * later, {@code move}) carry no actor argument: the actor is ambient, pulled through here.
 *
 * <p>The id is returned as a <b>primitive</b>, not a {@code PlayerId} value object, because its source
 * is human-authored configuration — invalid-capable, like the YAML world seed. It therefore crosses
 * as a {@code String} and the {@code PlayerId} validity gate stays inside the use case (the one place
 * all validation lands); a malformed configured id surfaces as a use-case outcome, not a construction
 * failure in the adapter.
 */
public interface PlayerOperationsOutputPort {

    /**
     * @return the id of the player currently acting, as a primitive carrier — the use case constructs
     *         and validates the {@code PlayerId}.
     */
    String currentPlayerId();
}
