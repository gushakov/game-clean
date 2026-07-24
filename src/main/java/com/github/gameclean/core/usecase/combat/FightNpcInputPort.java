package com.github.gameclean.core.usecase.combat;

import java.util.List;

/**
 * Driving (input) port for the <b>FightNpc</b> user goal: the player and an NPC come to blows in the player's
 * current scene. A <b>multi-actor</b> use case — the <em>player</em> is the primary actor (they initiate the
 * fight by striking), and once provoked the <em>NPC</em> is a secondary actor initiating its own step (it
 * strikes back). Both actors' blows land through this one use case, because they are the same interaction seen
 * from two sides; the class is the namespace for the whole combat scenario (Cockburn multi-actor use case).
 *
 * <p><b>Player-initiated (primary actor).</b> Like {@code take}, a two-interaction designation — the player
 * names the target by description or, when that is ambiguous, by choosing from a numbered menu — converging on
 * one strike that lowers the NPC's hit points and, if it survives, provokes it into the hostile stance:
 *
 * <ul>
 *   <li>{@link #playerHitsTarget(String)} — designate by a descriptive fragment (the disambiguation lives in
 *       the {@code select} subcase: no match, exactly one, or a menu offered).</li>
 *   <li>{@link #playerHitsChosenCandidate(int, List)} — designation by choosing from the last-offered
 *       candidates, completing the disambiguation.</li>
 * </ul>
 *
 * <p><b>NPC-initiated (secondary actor).</b> {@link #npcStrikesPlayer(String)} is the counterstrike step: a
 * provoked, co-located NPC swings at the player. It is <em>not</em> invoked by the terminal — the animate
 * policy decides it (from persisted stance + co-location + the attack gate) and dispatches it as a command,
 * which a driving adapter (the NPC command session) turns into this call. The methodology's own Cockburn
 * table sanctions a secondary actor <em>initiating a step</em> (the orthodox "the system enlists it" reading
 * is answered there); this is that live example.
 *
 * <p>The outcome stripes resolve synchronously within each interaction and are reported through
 * {@link FightNpcPresenterOutputPort}; every method is {@code void}. Primitive ids cross the boundary (the use
 * case constructs the {@code NpcId}); the player-designation methods take the offered tokens as a value
 * (dependency rejection), exactly as {@code take} does.
 */
public interface FightNpcInputPort {

    /**
     * The player designates an NPC to strike <em>by description</em>. Resolves the living NPCs in the player's
     * current scene and either strikes the single match, reports nothing-matches, or — when the fragment is
     * ambiguous — offers the candidates to disambiguate.
     *
     * @param target the player's free-text fragment (non-blank; the driving adapter supplies the trimmed
     *               remainder of the command line)
     */
    void playerHitsTarget(String target);

    /**
     * The player designates an NPC to strike <em>by choosing from the candidates last offered</em> — the
     * completion of the disambiguation flow. The pick is resolved against the offered tokens (out of range →
     * no such option) and re-validated against live scene state before the strike (gone → no longer here; lost
     * race at commit → got away).
     *
     * @param ordinal       the 1-based menu number the player picked
     * @param offeredTokens the candidate id tokens last offered, in display order — supplied as a value by the
     *                      driving adapter, empty when no offer is pending
     */
    void playerHitsChosenCandidate(int ordinal, List<String> offeredTokens);

    /**
     * The provoked NPC strikes back at the player — the secondary actor's counterstrike step, dispatched as a
     * command by the animate policy rather than typed by anyone. It <b>re-validates at execution</b> (the
     * decision was derived from a snapshot a moment earlier): the NPC may be gone or dead, the player absent,
     * already slain, or moved out of the NPC's scene between decision and blow. Those are quiet execution
     * stripes, not player-facing errors; a landed blow lowers the player's hit points and is narrated above the
     * prompt, and a lethal blow presents the player-slain outcome. (An already-dead player is a quiet stripe —
     * a corpse is not struck-and-slain again; ending the game on player death is a separate, deferred concern.)
     *
     * @param npcId the striking NPC's id, as a primitive carrier — the use case constructs and validates the
     *              {@code NpcId}
     */
    void npcStrikesPlayer(String npcId);
}
