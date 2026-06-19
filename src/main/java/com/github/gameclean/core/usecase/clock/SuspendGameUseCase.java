package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Banks the live session's elapsed time into the world clock as the player leaves — the Model B "pause on
 * quit" write. Implementation of {@link SuspendGameInputPort}; framework-free, wired by the composition root,
 * exercised in isolation against mocked ports.
 *
 * <p>It is the time-system twin of {@code move}: the read and the banking arithmetic run <em>outside</em> any
 * transaction (load the clock, ask the time source how long the session ran, let the clock
 * {@link GameClock#accumulate(long) accumulate} that into a new total), and a single
 * {@link TransactionOperationsOutputPort#doInTransaction} holds only the save. The acknowledgement is deferred
 * to after-commit, so the player is never told their progress was saved before it durably is, and the
 * interaction ends there — nothing runs past a presentation.
 *
 * <p>A missing clock (the game not yet in a playable state) is an <em>anticipated precondition</em>, handled
 * <em>before</em> the transaction by <b>branch-and-present</b> ({@code presentGameNotInitialized}) and a
 * {@code return} — never by throwing a technical exception into the catch-all. Only genuine faults (a
 * {@code PersistenceOperationsError} or a transaction-demarcation failure — a write fault has already rolled
 * back before propagating out of {@code doInTransaction}) ride the outermost {@code catch} to
 * {@code presentError}. Exactly one {@code present*} is reached on every path.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SuspendGameUseCase implements SuspendGameInputPort {

    SuspendGamePresenterOutputPort presenter;
    GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    GameTimeSourceOutputPort gameTimeSourceOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void playerLeavesTheGame() {
        try {
            // Precondition: the game must be in a playable state. A missing clock is an anticipated
            // outcome, presented and returned before any transaction is opened — not thrown into the catch-all.
            Optional<GameClock> clock = gameClockRepositoryOps.findClock();
            if (clock.isEmpty()) {
                presenter.presentGameNotInitialized();
                return;
            }

            // Bank the session's elapsed seconds (read + arithmetic outside the transaction).
            GameClock banked = clock.get().accumulate(gameTimeSourceOps.elapsedSessionSeconds());

            // One write, one atomic unit; acknowledge only after the bank commits, then end.
            txOps.doInTransaction(false, () -> {
                gameClockRepositoryOps.saveClock(banked);
                txOps.doAfterCommit(presenter::presentGameSuspended);
            });

        } catch (Exception e) {
            // Outermost checkpoint: only genuine faults reach here — a PersistenceOperationsError (already
            // rolled back) or a TransactionOperationsError.
            presenter.presentError(e);
        }
    }
}
