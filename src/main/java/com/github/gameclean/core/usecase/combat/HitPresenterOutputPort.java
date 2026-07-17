package com.github.gameclean.core.usecase.combat;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Hit}, co-located with its use case. It carries only {@code hit}'s
 * <em>own</em> outcomes plus the inherited catch-all; the orient not-founds and the disambiguation outcomes
 * belong to the {@code orient} and {@code select} subcase ports respectively, which the concrete terminal
 * presenter implements alongside this one as three flat interfaces (composition, not a presenter base class) —
 * exactly as {@code take} composes them.
 *
 * <p><b>Three outcomes.</b> A landed strike splits by whether it killed: {@link #presentNpcStruck(Npc, int)}
 * carries the surviving NPC (post-damage, so the renderer can show remaining health) and the damage dealt;
 * {@link #presentNpcSlain(Npc)} is the kill stripe (hit points reached zero). {@link #presentNpcGotAway(NpcId)}
 * is the <em>write-side</em> lost race — we read the NPC as present but the wandering ticker's write committed
 * first, so this strike's versioned write was rejected — the twin of {@code select}'s read-side
 * {@code presentTargetNoLongerAvailable}.
 *
 * <p>Domain objects pass straight through ({@link Npc}, {@link NpcId}) — no response DTOs.
 */
public interface HitPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: the NPC was struck for {@code damage} and survived (its hit points are positive). */
    void presentNpcStruck(Npc npc, int damage);

    /** The strike was lethal: the NPC's hit points reached zero and it is dead. */
    void presentNpcSlain(Npc npc);

    /**
     * The strike lost a concurrent race: the NPC was present when selected but another writer (the wandering
     * ticker) committed first, so this strike's versioned write was rejected and rolled back. The honest
     * "it got away" outcome — the write-side twin of {@code select}'s read-side
     * {@code presentTargetNoLongerAvailable}.
     */
    void presentNpcGotAway(NpcId npcId);
}
