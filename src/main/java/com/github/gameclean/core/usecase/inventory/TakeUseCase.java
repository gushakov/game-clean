package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
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
 * designations converge on {@link #takeResolvedItem}: the item is moved into the player's keeping
 * ({@code item.takenBy}) and saved inside one narrow {@link TransactionOperationsOutputPort#doInTransaction
 * read-write transaction}, with the success presented only <em>after commit</em> so the player is never told
 * the item is theirs before the move is durable.
 *
 * <p><b>Concurrency is closed authoritatively here.</b> A ground item is contested, so the transaction uses
 * the {@code (action, onLockDetected)} overload: the item's optimistic-locking version makes the
 * last-writer-wins race fail at commit, and that lost race is presented as {@code presentItemGotAway} — the
 * write-side twin of {@code select}'s advisory read-side {@code presentItemNoLongerHere}. The handler presents,
 * so the {@code doInTransaction} is the interaction's terminal act (no statement follows it).
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); the success path presents after commit; a lost
 * race presents via {@code onLockDetected}; the outermost {@code catch} routes anything unhandled (a malformed
 * remembered token, a {@code PersistenceOperationsError} that has already rolled back) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TakeUseCase implements TakeInputPort {

    TakePresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort selectTargetSubcase;
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
     * The shared write tail: move the resolved item into the holder's keeping and persist it in one narrow
     * transaction, presenting success after commit and a lost concurrent race via {@code onLockDetected}. Void
     * and terminal — it ends in a presentation on every path, so callers do nothing after it.
     */
    private void takeResolvedItem(PlayerId holder, Item item) {
        Item taken = item.takenBy(holder);
        txOps.doInTransaction(
                () -> {
                    itemOps.saveItem(taken);
                    txOps.doAfterCommit(() -> presenter.presentItemTaken(taken));
                },
                () -> presenter.presentItemGotAway(item.getId()));
    }
}
