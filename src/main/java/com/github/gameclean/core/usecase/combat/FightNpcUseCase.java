package com.github.gameclean.core.usecase.combat;

import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.corpse.CorpseBlueprint;
import com.github.gameclean.core.port.corpse.CorpseBlueprintSourceOperationsOutputPort;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The combat use case — the player and NPCs come to blows. Implementation of {@link FightNpcInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked subcases and ports (a
 * {@code ScriptedDice} pins the damage rolls). It carries interactions for <em>both</em> actors of the fight:
 * the player striking an NPC (primary actor) and a provoked NPC striking back (secondary actor).
 *
 * <p><b>Player strikes (primary actor).</b> Like {@code take} it is pure orchestration over two orthogonal
 * subcases — {@code orient} resolves <em>where</em> the player stands, {@code select} (bound to an
 * NPC-in-scene candidate) resolves <em>which</em> NPC they mean — and it <b>writes</b>. It holds no
 * value-object-construction checkpoint on this path: {@code orient} hands it a valid scene and {@code select} a
 * valid NPC, so it rolls damage, lowers the NPC copy-on-write, provokes the survivor into the hostile stance,
 * and persists. The struck-vs-slain stripe is chosen from the post-damage NPC ({@code struck.isDead()}); a
 * survivor is {@linkplain Npc#provoked() provoked} so the animate policy will re-derive its counterattack.
 *
 * <p><b>A slaying replaces the NPC with its corpse (#93).</b> When the strike kills and the NPC carries a
 * {@code corpseRef}, the blueprint is pulled through the corpse-blueprint port and the corpse — an anchored
 * container item minted where the NPC fell — plus its rolled loot are built <em>outside</em> the transaction
 * (a read and pure entropy, like the damage roll); one transaction then atomically deletes the NPC row and
 * saves the remains, so a death never commits without its corpse nor vice versa (no half-death — a blueprint
 * failure propagates to the catch-all and the strike does not land at all). An NPC authored without a corpse
 * simply has its row deleted. The delete is version-checked like the save, so a lost race still presents
 * {@code presentNpcGotAway}.
 *
 * <p><b>NPC strikes back (secondary actor).</b> {@link #npcStrikesPlayer(String)} is dispatched by the animate
 * policy as a command (never typed), so its actor is the NPC and there is no {@code orient}/{@code select}
 * opening. Because the decision was derived from a snapshot a moment earlier, it <b>re-validates at
 * execution</b>: the NPC must still be present and alive, the player present <em>and alive</em>, and the two
 * co-located ({@code player.currentScene == npc.currentScene}) — any miss is a quiet
 * {@code presentNothingHappened} stripe (a whiff the player has no error for; the loop re-derives next tick).
 * The player-alive check is what stops a corpse being struck-and-slain every round once the player has died
 * (player-death consequences — ending the session — are a separate, deferred concern). Only then does it roll
 * damage, lower the player, and persist.
 *
 * <p><b>Concurrency, both sides.</b> An NPC is contested (the player's strike races the wandering/attacking
 * policy's executions), so the player-strike uses the {@code (action, onLockDetected)} overload and a lost race
 * presents {@code presentNpcGotAway}. The player is now contested too (an NPC counterstrike races the player's
 * own {@code move}), so the counterstrike likewise uses the overload and a lost race presents the quiet stripe —
 * the loop re-derives. Both handlers present, so each {@code doInTransaction} is its interaction's terminal act.
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); a success presents after commit; a lost race
 * presents via {@code onLockDetected}; a counterstrike miss presents the quiet stripe; the outermost
 * {@code catch} routes anything unhandled to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class FightNpcUseCase implements FightNpcInputPort {

    /** Step-1 damage is a flat d10 — every landed strike does 1..10. Weapons and stats wait for their goal. */
    static final int DAMAGE_DIE_SIDES = 10;

    FightNpcPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<SceneId, Npc> selectTargetSubcase;
    NpcRepositoryOperationsOutputPort npcOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    PlayerOperationsOutputPort playerOps;
    ItemRepositoryOperationsOutputPort itemOps;
    CorpseBlueprintSourceOperationsOutputPort corpseBlueprintSourceOps;
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

    @Override
    public void npcStrikesPlayer(String npcId) {
        try {
            // Initiating actor: the NPC (secondary actor), dispatched as a command by the animate policy.
            NpcId strikerId = NpcId.of(npcId);

            // Re-validate the decision the policy derived from a snapshot: the NPC must still be present and
            // alive, the player present, and the two co-located. Each miss is a quiet no-op — an autonomous
            // counterstrike has no player-facing error, and the loop re-derives next tick.
            Optional<Npc> npcOpt = npcOps.findNpc(strikerId);
            if (npcOpt.isEmpty()) {
                presenter.presentNothingHappened();     // NPC gone or dead since the decision
                return;
            }
            Npc npc = npcOpt.get();

            Optional<Player> playerOpt =
                    playerRepositoryOps.findPlayer(PlayerId.of(playerOps.currentPlayerId()));
            if (playerOpt.isEmpty()) {
                presenter.presentNothingHappened();     // no player to strike
                return;
            }
            Player player = playerOpt.get();

            if (player.isDead()) {
                presenter.presentNothingHappened();     // the player is already slain — a corpse is not struck
                return;
            }

            if (!player.getCurrentScene().equals(npc.getCurrentScene())) {
                presenter.presentNothingHappened();     // player moved out of the NPC's scene — a whiff
                return;
            }

            // Roll the blow outside the transaction (a pure choice, no persistence effect) and lower the player.
            int damage = dice.rollDie(DAMAGE_DIE_SIDES);
            Player struck = player.takeDamage(damage);

            // One write, one atomic unit. The player is contested (the counterstrike races the player's own
            // move), so a lost lock race is the quiet stripe — the loop re-derives. Present only after commit.
            txOps.doInTransaction(
                    () -> {
                        playerRepositoryOps.savePlayer(struck);
                        txOps.doAfterCommit(() -> {
                            if (struck.isDead()) {
                                presenter.presentPlayerSlain(npc, damage);
                            } else {
                                presenter.presentNpcStruckPlayer(npc, damage, struck);
                            }
                        });
                    },
                    presenter::presentNothingHappened);

        } catch (Exception e) {
            // Outermost checkpoint: a malformed dispatched id, a PersistenceOperationsError (already rolled
            // back), or an unexpected bug ends here.
            presenter.presentError(e);
        }
    }

    /**
     * The shared player-strike tail: roll the damage (outside the transaction), lower the resolved NPC
     * copy-on-write, and persist the consequence in one narrow transaction — a survivor is provoked and saved;
     * a slain NPC's row is atomically replaced by its minted corpse and loot. Each branch presents its outcome
     * after commit and a lost concurrent race via {@code onLockDetected}. Void and terminal — it ends in a
     * presentation on every path, so callers do nothing after it.
     */
    private void strikeResolvedNpc(Npc npc) {
        int damage = dice.rollDie(DAMAGE_DIE_SIDES);
        Npc struck = npc.takeDamage(damage);

        if (!struck.isDead()) {
            // A survivor is provoked into the hostile stance so the animate policy re-derives its counterattack.
            Npc provoked = struck.provoked();
            txOps.doInTransaction(
                    () -> {
                        npcOps.saveNpc(provoked);
                        txOps.doAfterCommit(() -> presenter.presentNpcStruck(provoked, damage));
                    },
                    () -> presenter.presentNpcGotAway(npc.getId()));
            return;
        }

        // The strike is lethal. Mint what the death leaves behind — the corpse (when authored) and its rolled
        // loot — outside the transaction: the blueprint pull is a read and the rolls are pure entropy, neither
        // with a persistence effect. A blueprint failure propagates to the caller's catch-all before anything
        // is written, so the strike fails whole (no half-death).
        List<Item> remains = mintRemains(struck);
        Optional<Item> corpse = remains.isEmpty() ? Optional.empty() : Optional.of(remains.getFirst());

        // One atomic unit replaces the NPC with its remains: the version-checked delete (a lost race must not
        // slay an NPC another writer has moved past) and the corpse + loot inserts commit together — a death
        // never commits without its corpse, nor a corpse without its death.
        txOps.doInTransaction(
                () -> {
                    npcOps.deleteNpc(struck);
                    remains.forEach(itemOps::saveItem);
                    txOps.doAfterCommit(() -> presenter.presentNpcSlain(struck, corpse));
                },
                () -> presenter.presentNpcGotAway(npc.getId()));
    }

    /**
     * Builds the items a slaying leaves behind: empty for an NPC authored without a corpse; otherwise the
     * corpse instance minted at the death scene (always first), followed by whichever loot entries' odds roll
     * a hit — one roll per entry, each minted {@code Inside} the corpse (the containment-fill semantics,
     * replayed at death time). Pure construction over the pulled blueprint — no persistence effect.
     */
    private List<Item> mintRemains(Npc slain) {
        if (slain.getCorpseRef() == null) {
            return List.of();
        }
        CorpseBlueprint blueprint = corpseBlueprintSourceOps.loadCorpseBlueprint(slain.getCorpseRef());
        List<Item> remains = new ArrayList<>();
        Item corpse = blueprint.getCorpseTemplate().instanceAt(ItemId.mint(dice), slain.getCurrentScene());
        remains.add(corpse);
        for (CorpseBlueprint.Loot loot : blueprint.getLoot()) {
            loot.getTemplate().spawnInside(dice, loot.getChance(), corpse.getId()).ifPresent(remains::add);
        }
        return remains;
    }
}
