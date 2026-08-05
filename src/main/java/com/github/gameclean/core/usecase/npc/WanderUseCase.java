package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
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
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Executes one NPC wander dispatched by the animate policy. Implementation of {@link WanderInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked ports. The initiating
 * actor is the NPC (its wander was decided upstream by the policy and dispatched as a command); this interaction
 * only <em>executes</em> the decided move.
 *
 * <p><b>Re-validate, then move.</b> The policy decided this wander from a snapshot a moment earlier, so the
 * execution re-checks its preconditions before writing: the NPC must still be present and alive
 * ({@code findNpc}), its current scene resolvable, the named exit still present on it, and that exit's target
 * resolvable. Any miss is a quiet no-op — an autonomous mover has no audience for a failure, and the loop
 * re-derives next tick. The exit was chosen by the policy and carried in the command, so this interaction never
 * re-rolls; it just resolves the exit by name.
 *
 * <p><b>One write, one atomic unit, and narrate only what the player can witness.</b> The reads and validity
 * checks run outside the transaction; a single {@link TransactionOperationsOutputPort#doInTransaction} holds
 * only the {@code saveNpc} write, using the {@code (action, onLockDetected)} overload because an NPC is
 * contested (the same NPC's other executions, the player's strike) — a lost race presents the quiet stripe and
 * the loop re-derives. After commit, the movement is narrated <em>only if perceptible</em> from where the player
 * stands (its source or target is the player's current scene); otherwise it is the quiet stripe. Exactly one
 * {@code present*} is reached on every path; the outermost {@code catch} routes anything unhandled (a
 * {@code PersistenceOperationsError} that has already rolled back, a malformed dispatched id) to
 * {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class WanderUseCase implements WanderInputPort {

    WanderPresenterOutputPort presenter;
    NpcRepositoryOperationsOutputPort npcOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void npcWandersThrough(String npcId, String exitName) {
        try {
            NpcId id = NpcId.of(npcId);

            // Re-validate: the NPC must still be present and alive.
            Optional<Npc> npcOpt = npcOps.findNpc(id);
            if (npcOpt.isEmpty()) {
                presenter.presentNothingHappened();     // gone or dead since the decision
                return;
            }
            Npc npc = npcOpt.get();

            // Its current scene must resolve, and the named exit must still be present on it.
            Optional<Scene> fromOpt = sceneOps.findScene(npc.getCurrentScene());
            if (fromOpt.isEmpty()) {
                presenter.presentNothingHappened();
                return;
            }
            Scene from = fromOpt.get();
            Optional<Exit> exit = from.exitNamed(exitName);
            if (exit.isEmpty()) {
                presenter.presentNothingHappened();      // the exit vanished since the decision
                return;
            }

            // The exit's target must resolve (an inter-aggregate reference that may dangle).
            SceneId targetId = exit.get().getTarget();
            Optional<Scene> toOpt = sceneOps.findScene(targetId);
            if (toOpt.isEmpty()) {
                presenter.presentNothingHappened();
                return;
            }
            Scene to = toOpt.get();

            Npc moved = npc.moveTo(targetId);

            // Where the player stands — to decide whether this wander is perceptible. A read, outside the
            // transaction; its absence means nothing is perceptible.
            SceneId playerScene = playerRepositoryOps
                    .findPlayer(PlayerId.of(playerOps.currentPlayerId()))
                    .map(Player::getCurrentScene)
                    .orElse(null);
            PerceivedNpcMovement perceived = perceptibleMovement(moved, from, to, exitName, playerScene);

            // One write, one atomic unit; narrate after commit only if perceptible, else the quiet stripe. A
            // lost lock race is quiet too (the loop re-derives). The interaction ends here.
            txOps.doInTransaction(
                    () -> {
                        npcOps.saveNpc(moved);
                        txOps.doAfterCommit(() -> {
                            if (perceived != null) {
                                presenter.presentNpcMovement(perceived);
                            } else {
                                presenter.presentNothingHappened();
                            }
                        });
                    },
                    presenter::presentNothingHappened);

        } catch (Exception e) {
            // Outermost checkpoint: a malformed dispatched id or a PersistenceOperationsError ends here.
            presenter.presentError(e);
        }
    }

    /**
     * Classifies the move against the player's scene: a move <em>from</em> it is a {@link MovementKind#DEPARTED}
     * (detail = the exit name it left by), a move <em>into</em> it is an {@link MovementKind#ARRIVED} (detail =
     * the name of the scene it came from); a move touching neither is off-stage. A null player scene yields no
     * perceptible movement.
     */
    private static PerceivedNpcMovement perceptibleMovement(Npc moved, Scene from, Scene to, String exitName,
                                                            SceneId playerScene) {
        if (playerScene == null) {
            return null;
        }
        if (from.getId().equals(playerScene)) {
            return new PerceivedNpcMovement(moved, MovementKind.DEPARTED, exitName);
        }
        if (to.getId().equals(playerScene)) {
            return new PerceivedNpcMovement(moved, MovementKind.ARRIVED, from.getName());
        }
        return null;
    }
}
