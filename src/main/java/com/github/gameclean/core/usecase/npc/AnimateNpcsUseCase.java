package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Advances the world's NPCs one tick of autonomous activity. Implementation of {@link AnimateNpcsInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked ports. The initiating
 * actor is the <em>system</em> (the background NPC-activity ticker fires it on a fixed real-time interval), so
 * there is no security assertion — the peer of {@code AnnounceTimeOfDay}.
 *
 * <p><b>A blind metronome drives a smart interaction.</b> The ticker carries no NPC knowledge; it just fires
 * this repeatedly. So this use case owns all of it: enumerate the NPCs, roll each one's authored move chance,
 * and on a hit pick a random exit and wander it to the adjacent scene. The dice rolls run <em>outside</em> the
 * transaction (a pure choice with no persistence effect, like the item-spawn rolls), made by an injected
 * {@link Dice} — the game's own source of chance — so the interaction stays deterministic under test.
 *
 * <p><b>Autonomous movers have no audience for their own failures.</b> An NPC whose current scene cannot be
 * resolved, or that stands in a dead-end (no exits), or that rolls a move into a dangling exit target, is
 * <em>skipped silently</em>: unlike a player command there is no actor to present a "you cannot go that way"
 * outcome to, so the tick simply moves the NPCs that can move.
 *
 * <p><b>Narration is filtered to what the player can witness.</b> Only movements whose source or target is the
 * player's current scene are narrated — a departure the player sees leave, or an arrival into their room; every
 * other move happens off-stage and is presented as the quiet outcome. The player's scene is resolved inline
 * (not through the {@code orient} prologue, which this system interaction does not share) and its absence is
 * tolerated — a tick with no resolvable player narrates nothing but still persists the moves.
 *
 * <p><b>One write, one atomic unit, single-writer.</b> A single {@code doInTransaction} saves every moved NPC —
 * the <em>plain</em> overload with no {@code onLockDetected}, because the ticker is the NPC's only writer today,
 * so a lock loss is unreachable (the {@code drop} case, not {@code take}). The one presentation is deferred to
 * after-commit so the player is never told an NPC moved before the move is durable, and the interaction returns
 * immediately after registering it. Exactly one {@code present*} is reached on every path: an empty world, a
 * tick where nothing moved, or a tick with no perceptible movement all present {@code presentNothingHappened}
 * (the first two before any transaction); the outermost {@code catch} routes any unhandled fault (a
 * {@code PersistenceOperationsError} that has already rolled back, or an unexpected bug) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AnimateNpcsUseCase implements AnimateNpcsInputPort {

    AnimateNpcsPresenterOutputPort presenter;
    NpcRepositoryOperationsOutputPort npcOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    Dice dice;
    TransactionOperationsOutputPort txOps;

    @Override
    public void systemAdvancesNpcs() {
        try {
            // Initiating actor: the system (the NPC ticker) — no security assertion is required.

            // Enumerate the NPCs. An empty world is the safe pre-initialization no-op (the ticker may fire
            // before the world is seeded): present the quiet outcome and return, with no transaction.
            List<Npc> npcs = npcOps.findAllNpcs();
            if (npcs.isEmpty()) {
                presenter.presentNothingHappened();
                return;
            }

            // Roll each NPC's move (outside any transaction — a pure choice, no persistence effect). A move
            // whose current scene is unresolvable, whose scene is a dead-end, or whose chosen exit dangles is
            // skipped silently: an autonomous mover has no audience for a failure.
            List<NpcMove> moves = new ArrayList<>();
            for (Npc npc : npcs) {
                Optional<Scene> from = sceneOps.findScene(npc.getCurrentScene());
                if (from.isEmpty() || from.get().getExits().isEmpty()) {
                    continue;
                }
                if (!dice.roll(npc.getMoveChance())) {
                    continue;
                }
                Exit chosen = dice.pick(from.get().getExits());
                Optional<Scene> to = sceneOps.findScene(chosen.getTarget());
                if (to.isEmpty()) {
                    continue;
                }
                moves.add(new NpcMove(npc.moveTo(chosen.getTarget()), from.get(), to.get(), chosen.getName()));
            }

            // Nothing moved this tick: present the quiet outcome and return, with no transaction.
            if (moves.isEmpty()) {
                presenter.presentNothingHappened();
                return;
            }

            // Resolve where the player stands, to filter the movements they can witness. A read, so it runs
            // outside the transaction; its absence is tolerated (no player scene => nothing is perceptible).
            SceneId playerScene = playerRepositoryOps
                    .findPlayer(new PlayerId(playerOps.currentPlayerId()))
                    .map(Player::getCurrentScene)
                    .orElse(null);
            List<PerceivedNpcMovement> perceptible = perceptibleMovements(moves, playerScene);

            // One write, one atomic unit. Save every moved NPC (plain transaction — single-writer, no
            // lock-loss handler), then narrate any perceptible movement only after the moves commit; the
            // quiet outcome covers a tick that moved NPCs only off-stage. The interaction ends here.
            txOps.doInTransaction(false, () -> {
                moves.forEach(move -> npcOps.saveNpc(move.getMovedNpc()));
                txOps.doAfterCommit(() -> {
                    if (perceptible.isEmpty()) {
                        presenter.presentNothingHappened();
                    } else {
                        presenter.presentNpcMovements(perceptible);
                    }
                });
            });
            return;

        } catch (Exception e) {
            // Outermost checkpoint: a PersistenceOperationsError (already rolled back), a malformed configured
            // player id, or an unexpected bug ends here.
            presenter.presentError(e);
        }
    }

    /**
     * Classifies each move against the player's current scene: a move <em>from</em> that scene is a
     * {@link MovementKind#DEPARTED} (detail = the exit name it left by), a move <em>into</em> it is an
     * {@link MovementKind#ARRIVED} (detail = the name of the scene it came from); a move touching neither is
     * off-stage and dropped. A null player scene (unresolved player) yields no perceptible movements.
     */
    private static List<PerceivedNpcMovement> perceptibleMovements(List<NpcMove> moves, SceneId playerScene) {
        List<PerceivedNpcMovement> perceptible = new ArrayList<>();
        if (playerScene == null) {
            return perceptible;
        }
        for (NpcMove move : moves) {
            if (move.getFrom().getId().equals(playerScene)) {
                perceptible.add(new PerceivedNpcMovement(move.getMovedNpc(), MovementKind.DEPARTED, move.getExitName()));
            } else if (move.getTo().getId().equals(playerScene)) {
                perceptible.add(new PerceivedNpcMovement(move.getMovedNpc(), MovementKind.ARRIVED, move.getFrom().getName()));
            }
        }
        return perceptible;
    }

    /**
     * Use-case-private record of one resolved NPC move: the NPC already moved to its new scene (the value to
     * persist), plus the source and target scenes and the exit taken — the context {@link #perceptibleMovements}
     * needs to classify and phrase a witnessed movement. Not a domain concept, so it stays out of the model.
     */
    @Value
    private static class NpcMove {
        Npc movedNpc;
        Scene from;
        Scene to;
        String exitName;
    }
}
