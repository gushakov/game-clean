package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Puts a carried item down onto the ground of the player's current scene. Implementation of
 * {@link DropInputPort}; framework-free, wired by the composition root, exercised in isolation against
 * mocked subcases and ports.
 *
 * <p>{@code take}'s mirror image, and a perfect criss-cross over the same two orthogonal subcases:
 * <b>take selects by scene and mutates by player; drop selects by player and mutates by scene.</b> The
 * {@code orient} prologue resolves both coordinates — the player whose keeping the inventory-sourced
 * {@code select} reads, and the scene the item lands in. Like {@code take}, pure orchestration with no
 * value-object-construction checkpoint: every input is already a valid domain object.
 *
 * <p><b>The write tail, shared by both interactions.</b> Once {@code select} returns the resolved item, both
 * designations converge on {@link #dropResolvedItem}: the item is put down ({@code item.droppedAt}) and
 * saved inside one narrow read-write transaction, with the success presented only <em>after commit</em> so
 * the player is never told the item is on the ground before the move is durable.
 *
 * <p><b>Deliberately the plain transaction overload — the single-writer contrast to {@code take}.</b> A held
 * item is written only by its holder, so there is no concurrent race to lose and the
 * {@code (action, onLockDetected)} idiom would be machinery for an unreachable outcome. The versioned save
 * still guards integrity; were a lock loss ever to occur it would be a wiring surprise, and it propagates to
 * the outermost {@code catch → presentError} like any fault (design-notes §5: contested→handler,
 * single-writer→propagate).
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); the success path presents after commit; the
 * outermost {@code catch} routes anything unhandled to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DropUseCase implements DropInputPort {

    DropPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<PlayerId, Item> selectTargetSubcase;
    ItemRepositoryOperationsOutputPort itemOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void playerDropsTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Item item = selectTargetSubcase.playerDesignatesTarget(target, bearings.getPlayer().getId());
            dropResolvedItem(bearings.getScene().getId(), item);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerDropsChosenCandidate(int ordinal, List<String> offeredCandidateTokens) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Item item = selectTargetSubcase.playerDesignatesChosenCandidate(
                    ordinal, offeredCandidateTokens, bearings.getPlayer().getId());
            dropResolvedItem(bearings.getScene().getId(), item);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * The shared write tail: put the resolved item down in the destination scene and persist it in one narrow
     * transaction, presenting success after commit. Void and terminal — it ends in a presentation on every
     * path, so callers do nothing after it. Plain overload on purpose: a lock loss on a single-writer held
     * item is unreachable, so it is left to propagate as a fault rather than handled as an outcome.
     */
    private void dropResolvedItem(SceneId destination, Item item) {
        Item dropped = item.droppedAt(destination);
        txOps.doInTransaction(false, () -> {
            itemOps.saveItem(dropped);
            txOps.doAfterCommit(() -> presenter.presentItemDropped(dropped));
        });
    }
}
