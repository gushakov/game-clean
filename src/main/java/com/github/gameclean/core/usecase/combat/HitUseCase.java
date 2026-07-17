package com.github.gameclean.core.usecase.combat;

import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Strikes an NPC in the player's current scene. Implementation of {@link HitInputPort}; framework-free, wired
 * by the composition root, exercised in isolation against mocked subcases and ports (a {@code ScriptedDice}
 * pins the damage roll).
 *
 * <p>Like {@code take} it is <b>pure orchestration over two orthogonal subcases</b> — {@code orient} resolves
 * <em>where</em> the player stands, {@code select} (bound to an NPC-in-scene candidate) resolves <em>which</em>
 * NPC they mean (it owns the candidate fetch and every disambiguation outcome) — and it <b>writes</b>. It holds
 * no value-object-construction checkpoint: {@code orient} hands it a valid scene and {@code select} a valid
 * NPC, so the only thing left is to roll damage, lower the NPC's hit points, and persist. Step 1 records no
 * attacker (hostility is #66 step 2), so it uses {@code orient} for the scene coordinate and grounding — a
 * player must stand somewhere to strike something there — without otherwise reading the player.
 *
 * <p><b>The strike tail, shared by both interactions.</b> Once {@code select} returns the resolved NPC, both
 * designations converge on {@link #strikeResolvedNpc}: the damage is rolled <em>outside</em> any transaction
 * (a pure {@link Dice#rollDie(int) d10}, deterministic under a scripted dice), the NPC is lowered copy-on-write
 * ({@code npc.takeDamage}), and the result is saved inside one narrow {@link TransactionOperationsOutputPort
 * read-write transaction}, with the outcome presented only <em>after commit</em> so the player is never told an
 * NPC is wounded or slain before the write is durable. The survived-vs-slain stripe is chosen from the
 * post-damage NPC ({@code struck.isDead()}).
 *
 * <p><b>Concurrency is closed authoritatively here.</b> An NPC is contested (the player's strike races the
 * wandering ticker), so the transaction uses the {@code (action, onLockDetected)} overload: the NPC's
 * optimistic-locking version makes the last-writer-wins race fail at commit, and that lost race is presented as
 * {@code presentNpcGotAway}. The handler presents, so the {@code doInTransaction} is the interaction's terminal
 * act (no statement follows it).
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); the success path presents after commit; a lost
 * race presents via {@code onLockDetected}; the outermost {@code catch} routes anything unhandled (a malformed
 * remembered token, a {@code PersistenceOperationsError} that has already rolled back) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class HitUseCase implements HitInputPort {

    /** Step-1 damage is a flat d10 — every landed strike does 1..10. Weapons and stats wait for their goal. */
    static final int DAMAGE_DIE_SIDES = 10;

    HitPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<SceneId, Npc> selectTargetSubcase;
    NpcRepositoryOperationsOutputPort npcOps;
    TransactionOperationsOutputPort txOps;
    Dice dice;

    @Override
    public void playerHitsTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Npc npc = selectTargetSubcase.playerDesignatesTarget(target, bearings.getScene().getId());
            strikeResolvedNpc(npc);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerHitsChosenCandidate(int ordinal, List<String> offeredTokens) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Npc npc = selectTargetSubcase.playerDesignatesChosenCandidate(
                    ordinal, offeredTokens, bearings.getScene().getId());
            strikeResolvedNpc(npc);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * The shared strike tail: roll the damage (outside the transaction), lower the resolved NPC copy-on-write,
     * and persist it in one narrow transaction — presenting the slain or struck outcome after commit and a lost
     * concurrent race via {@code onLockDetected}. Void and terminal — it ends in a presentation on every path,
     * so callers do nothing after it.
     */
    private void strikeResolvedNpc(Npc npc) {
        int damage = dice.rollDie(DAMAGE_DIE_SIDES);
        Npc struck = npc.takeDamage(damage);
        txOps.doInTransaction(
                () -> {
                    npcOps.saveNpc(struck);
                    txOps.doAfterCommit(() -> {
                        if (struck.isDead()) {
                            presenter.presentNpcSlain(struck);
                        } else {
                            presenter.presentNpcStruck(struck, damage);
                        }
                    });
                },
                () -> presenter.presentNpcGotAway(npc.getId()));
    }
}
