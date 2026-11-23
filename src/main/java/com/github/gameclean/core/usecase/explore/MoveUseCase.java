package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Lock;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Optional;

/**
 * Moves the player to an adjacent scene through a named exit, then describes the scene they enter.
 * Implementation of {@link MoveInputPort}; framework-free, wired by the composition root, exercised in
 * isolation against mocked ports.
 *
 * <p>It shares {@code look}'s opening exactly — resolve the ambient player, read it, resolve the current
 * scene — and that opening is now factored into the {@link OrientPlayerSubcaseInputPort orient subcase}
 * the two reuse. The subcase <em>returns</em> the player and their current scene on success, or presents
 * the not-found outcome and throws {@link SubcaseAlreadyPresented} on a missing player / dangling scene;
 * the dedicated checkpoint swallows that signal as a no-op. (Earlier this prologue was inline-duplicated
 * to avoid a subcase that "presents on failure and continues on success"; the guarded-prologue subcase
 * dissolves that straddle — each invocation either presents-and-throws or returns-without-presenting.)
 *
 * <p>It presents the scene the player <em>enters</em>, so it fetches the items on that target scene's
 * ground and the NPCs standing in it (not the current scene's) — both belong to the presented scene, which
 * is why they are resolved per use case rather than in the shared {@code orient} prologue.
 *
 * <p><b>A locked exit opens to whoever carries its key.</b> When the chosen exit carries a {@link Lock}, the
 * player's keeping is read and searched for an item that {@link Item#opens(Lock) opens} it — only items held
 * directly count, not those inside a carried container. None found: the move is refused as a presented outcome,
 * before the target is even resolved, with no transaction and no write. One found: the move proceeds exactly as
 * through an open exit, and the entered scene is presented together with the item that let the player through
 * (the first match). The keeping is read <em>only</em> when the exit has a lock. There is no stored lock state:
 * "locked" is relative to what the mover carries.
 *
 * <p>Unlike {@code look}, {@code move} <b>writes</b>: it records the player's new position. The reads and
 * the validity checks all run <em>outside</em> any transaction; a single
 * {@link TransactionOperationsOutputPort#doInTransaction} holds only the write, and the success
 * presentation is the statement after it, so the player is never shown the entered room before the move
 * is durable. Exactly one {@code present*} is reached on every path and it is the interaction's last act:
 * the subcase presents the two not-found outcomes; the no-such-exit, locked and dangling-target branches present
 * and return; the outermost {@code catch} routes anything unhandled (a malformed configured id, or a
 * {@code PersistenceOperationsError} that has already rolled back) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class MoveUseCase implements MoveInputPort {

    MovePresenterOutputPort presenter;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    ItemRepositoryOperationsOutputPort itemOps;
    NpcRepositoryOperationsOutputPort npcOps;
    TransactionOperationsOutputPort txOps;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;

    @Override
    public void playerMovesThrough(String exitName) {
        try {
            // Shared opening: resolve the acting player and the scene they stand in (orient subcase).
            OrientPlayerResult playerInScene = orientPlayerSubcase.playerGetsBearings();

            // Match the chosen exit among the current scene's exits (case-insensitive, in the domain).
            Optional<Exit> exit = playerInScene.getScene().exitNamed(exitName);
            if (exit.isEmpty()) {
                presenter.presentNoSuchExit(exitName);
                return;
            }

            // A locked exit lets through only a player carrying an item that opens it. The keeping is read only
            // here, and only directly held items count. No key: refuse, before resolving the target, no write.
            Optional<Item> openedWith = Optional.empty();
            if (exit.get().hasLock()) {
                Lock lock = exit.get().getLock();
                openedWith = itemOps.findItemsHeldBy(playerInScene.getPlayer().getId()).stream()
                        .filter(item -> item.opens(lock))
                        .findFirst();
                if (openedWith.isEmpty()) {
                    presenter.presentExitLocked(exit.get());
                    return;
                }
            }

            // Resolve the exit's target — an inter-aggregate reference that may dangle.
            SceneId targetId = exit.get().getTarget();
            Optional<Scene> targetScene = sceneOps.findScene(targetId);
            if (targetScene.isEmpty()) {
                presenter.presentTargetSceneNotFound(targetId);
                return;
            }

            // The items on the ground and the NPCs standing in the scene being entered — fetched for the
            // *target* scene (what the player will see), not the current one. Reads, so they run outside the
            // transaction.
            List<Item> itemsInEntered = itemOps.findItemsInScene(targetId);
            List<Npc> npcsInEntered = npcOps.findNpcsInScene(targetId);

            // One write, one atomic unit. Record the new position; present the entered scene only once the
            // move has committed, and end the interaction there — nothing runs past a presentation.
            Player moved = playerInScene.getPlayer().moveTo(targetId);
            Scene entered = targetScene.get();
            txOps.doInTransaction(false, () -> playerRepositoryOps.savePlayer(moved));
            presenter.presentSceneEntered(entered, itemsInEntered, npcsInEntered, openedWith);

        } catch (SubcaseAlreadyPresented e) {
            // The orient subcase already presented its outcome (missing player or dangling scene); no-op.
        } catch (Exception e) {
            // Outermost checkpoint: a malformed configured id or a PersistenceOperationsError ends here.
            presenter.presentError(e);
        }
    }
}
