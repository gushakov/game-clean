package com.github.gameclean.core.model.dice;

import java.util.List;

/**
 * The game's source of chance — a <em>domain</em> capability the model owns, not an infrastructure port. For an
 * RPG, dice <em>are</em> domain: the model already owns the probability semantics ({@link Chance},
 * {@code SpawnRule}, {@code DayPhase}), and the only piece that ever reached outside was the raw entropy. So the
 * model rolls its own dice rather than asking a {@code RandomnessOperationsOutputPort} for a {@code nextDouble()}.
 *
 * <p>The public surface is domain-meaningful — {@link #roll(Chance)} resolves a single odds-weighted yes/no, and
 * {@link #pick(List)} selects uniformly among options — so no caller handles a raw {@code double}. The two model
 * consumers ({@code SpawnRule.rollPlacements}, {@code DayPhase.pickMessage}) collaborate with a {@code Dice}
 * directly; the use case holds one and hands it in, exactly as it hands in a loaded aggregate. Nothing
 * infrastructural crosses into the model as a callable: a {@code Dice} is a model collaborator, and the source of
 * its entropy is confined to a clearly-labelled implementation.
 *
 * <p><b>Determinism is preserved with a domain abstraction, not a mocked port.</b> Production uses
 * {@link SystemDice} (real entropy, thread-safe); tests and any future reproducible-world-from-a-seed use
 * {@link SeededDice} (a fixed seed gives a fixed sequence). This is why dice are domain while wall-clock
 * <em>time</em> stays an infrastructure port ({@code GameTimeSourceOutputPort}, a value read once in the shell):
 * time is the world <em>outside</em> the game; dice are <em>part</em> of the game.
 */
public interface Dice {

    /**
     * Rolls a single odds-weighted decision against the given {@link Chance}: draws once and asks the chance
     * whether the draw hits. A {@code Chance} of {@code 0/n} never hits; {@code n/n} always hits.
     *
     * @param chance the odds to roll against
     * @return {@code true} when this roll hits the chance
     */
    boolean roll(Chance chance);

    /**
     * Picks one option uniformly at random from a non-empty list. The selection is uniform across the list and
     * never runs off the end (a draw arbitrarily close to 1 still selects the last option).
     *
     * @param options the options to choose among — must be non-empty
     * @param <T>     the option type
     * @return the chosen option
     */
    <T> T pick(List<T> options);
}
