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
 * Picks an item up off the ground into the player's keeping. Implementation of {@link TakeInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked subcases and ports.
 *
 * <p>Like {@code examine} it is <b>pure orchestration over two orthogonal subcases</b> — {@code orient}
 * resolves <em>where</em> the player stands, {@code select} resolves <em>which</em> ground item they mean (it
 * owns the candidate fetch and every disambiguation outcome) — but unlike {@code examine} it <b>writes</b>.
 * It holds no value-object-construction checkpoint: {@code orient} hands it a valid player and {@code select}
 * a valid item, so the only thing left is to move the item and persist it.
 *
 * <p><b>The write tail, shared by both interactions.</b> Once {@code select} returns the resolved item, both
 * designations converge on {@link #takeResolvedItem}. Its opening checkpoint is the <b>anchored refusal</b>:
 * an item fixed where it stands (an anchored chest, say) is designatable — the player may try — but the take
 * is refused as a presented business outcome ({@code presentItemAnchored}), decided here on the domain fact
 * before any transaction opens, exactly as {@code examine} branches on {@code isContainer()}. Past that gate
 * the item is moved into the player's keeping ({@code item.takenBy}) and saved inside one narrow
 * {@link TransactionOperationsOutputPort#doInTransactionWithResult read-write transaction} that holds the
 * write and nothing else. The block returns what it settled (a
 * {@link TakeAttempt}) and the presentation is the plain statement <em>after</em> it, so statement order is
 * the after-commit order: the player is never told the item is theirs before the move is durable.
 *
 * <p><b>Concurrency is closed authoritatively here.</b> A ground item is contested, so the transaction is the
 * lock-aware one: the item's optimistic-locking version makes the last-writer-wins race fail at the versioned
 * write, the transaction rolls back, and the block returns {@link TakeAttempt#GOT_AWAY} instead of raising.
 * That lost race is presented as {@code presentItemGotAway} — the write-side twin of {@code select}'s advisory
 * read-side {@code presentTargetNoLongerAvailable}. Both presentations use values resolved before the block
 * ({@code taken}, the item's id); the block contributes only which of the two fates occurred.
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); past the block the {@code if}/{@code else} on
 * the attempt presents the take or the lost race, never both and never neither; the outermost {@code catch}
 * routes anything unhandled (a malformed remembered token, a {@code PersistenceOperationsError} that has
 * already rolled back) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TakeUseCase implements TakeInputPort {

    TakePresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<SceneId, Item> selectTargetSubcase;
    ItemRepositoryOperationsOutputPort itemOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void playerTakesTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Item item = selectTargetSubcase.playerDesignatesTarget(target, bearings.getScene().getId());
            takeResolvedItem(bearings.getPlayer().getId(), item);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerTakesChosenCandidate(int ordinal, List<String> offeredCandidateTokens) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Item item = selectTargetSubcase.playerDesignatesChosenCandidate(
                    ordinal, offeredCandidateTokens, bearings.getScene().getId());
            takeResolvedItem(bearings.getPlayer().getId(), item);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * What the take's transaction settled: the versioned write committed, or it lost a concurrent race and
     * rolled back. Private to this use case — it never crosses the input port, it only carries the block's
     * one bit to the presentation that follows it.
     */
    private enum TakeAttempt { TAKEN, GOT_AWAY }

    /**
     * The shared write tail: refuse an anchored item, otherwise move the resolved item into the holder's
     * keeping, persist it in one narrow transaction, and present what the transaction settled. Void and
     * terminal — it ends in a presentation on every path, so callers do nothing after it.
     */
    private void takeResolvedItem(PlayerId holder, Item item) {
        // Checkpoint — an anchored item is fixed where it stands: a business refusal, presented and done,
        // before any write or transaction. The use case decides on the domain fact; the presenter renders.
        if (item.isAnchored()) {
            presenter.presentItemAnchored(item);
            return;
        }
        Item taken = item.takenBy(holder);

        // The write set, and nothing else: no presentation, no subcase, no nested transaction in here.
        TakeAttempt attempt = txOps.doInTransactionWithResult(
                () -> {
                    itemOps.saveItem(taken);
                    return TakeAttempt.TAKEN;
                },
                TakeAttempt.GOT_AWAY);

        // All presentation is after the block: the transaction has committed or rolled back by now.
        if (attempt == TakeAttempt.TAKEN) {
            presenter.presentItemTaken(taken);
        } else {
            presenter.presentItemGotAway(item.getId());
        }
    }
}
