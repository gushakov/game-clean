package com.github.gameclean.core.usecase.combat;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code FightNpc}, co-located with its use case. It carries the combat
 * outcomes of <em>both</em> actors plus the inherited catch-all; the orient not-founds and the disambiguation
 * outcomes belong to the {@code orient} and {@code select} subcase ports, which the concrete terminal presenter
 * implements alongside this one as flat interfaces (composition, not a presenter base class) — exactly as
 * {@code take} composes them.
 *
 * <p><b>Player-initiated stripes.</b> A landed player strike splits by whether it killed:
 * {@link #presentNpcStruck(Npc, int)} carries the surviving (post-damage, now-hostile) NPC and the damage;
 * {@link #presentNpcSlain(Npc)} is the kill. {@link #presentNpcGotAway(NpcId)} is the write-side lost race —
 * the NPC was read as present but a concurrent write (a wander, another strike) committed first — the twin of
 * {@code select}'s read-side {@code presentTargetNoLongerAvailable}.
 *
 * <p><b>NPC-initiated stripes.</b> A provoked NPC's counterstrike splits the same way:
 * {@link #presentNpcStruckPlayer(Npc, int, Player)} carries the striking NPC, the damage, and the surviving
 * player (post-damage, so the renderer can show remaining health); {@link #presentPlayerSlain(Npc, int)} is the
 * player's death. These are <em>asynchronous</em> outcomes (the animate policy dispatched the strike; a driving
 * adapter runs it while the player may be at the prompt), so the terminal presenter narrates them above the
 * live prompt. A whiff (player moved away), a gone/dead NPC or absent player at execution, and a lost lock race
 * present the quiet {@link #presentNothingHappened()} stripe — an autonomous counterstrike that did not
 * materialize has no player-facing error, but the interaction still presents exactly once (the terminal renders
 * it as silence, a trace log).
 *
 * <p>Domain objects pass straight through ({@link Npc}, {@link NpcId}, {@link Player}) — no response DTOs.
 */
public interface FightNpcPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Player's happy path: the NPC was struck for {@code damage} and survived (now hostile). */
    void presentNpcStruck(Npc npc, int damage);

    /** Player's strike was lethal: the NPC's hit points reached zero and it is dead. */
    void presentNpcSlain(Npc npc);

    /**
     * The player's strike lost a concurrent race: the NPC was present when selected but another writer committed
     * first, so this strike's versioned write was rejected and rolled back. The write-side twin of {@code
     * select}'s read-side {@code presentTargetNoLongerAvailable}.
     */
    void presentNpcGotAway(NpcId npcId);

    /**
     * NPC's happy path: the provoked NPC struck the player for {@code damage} and the player survived. Carries
     * the striking NPC (to name it) and the post-damage {@code survivor} (so the renderer can show the player's
     * remaining health). An asynchronous outcome — narrated above the prompt.
     */
    void presentNpcStruckPlayer(Npc npc, int damage, Player survivor);

    /**
     * The NPC's counterstrike was lethal: the player's hit points reached zero. Carries the striking NPC and the
     * damage of the killing blow. An asynchronous outcome — narrated above the prompt.
     */
    void presentPlayerSlain(Npc npc, int damage);

    /**
     * Quiet counterstrike: a provoked NPC's swing did not materialize — the player had moved out of its scene,
     * the NPC or the player was gone at execution, or a concurrent write won the lock race. Only
     * {@link #npcStrikesPlayer} reaches it (the player-initiated interactions never do); the terminal renders it
     * as silence (a trace log), but presenting it keeps "exactly one {@code present*} per run" honest.
     */
    void presentNothingHappened();
}
