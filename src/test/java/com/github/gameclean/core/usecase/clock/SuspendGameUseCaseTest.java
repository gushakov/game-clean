package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransaction;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransactionAndFireAfterCommit;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link SuspendGameUseCase} in isolation — every output port is mocked. It banks the
 * session's elapsed seconds into the clock and acknowledges after the write commits, so the transaction port
 * is stubbed to run its action inline and fire after-commit callbacks immediately (the persistence-failure
 * case instead lets the action's error propagate, as the real adapter would). Exactly one {@code present*} is
 * reached on every path.
 */
@ExtendWith(MockitoExtension.class)
class SuspendGameUseCaseTest {

    @Mock
    private SuspendGamePresenterOutputPort presenter;
    @Mock
    private GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    @Mock
    private GameTimeSourceOutputPort gameTimeSourceOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private SuspendGameUseCase useCase;

    @Test
    void banksTheSessionIntoTheClockAndAcknowledgesAfterCommit() {
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.of(new GameClock(1_000)));
        when(gameTimeSourceOps.elapsedSessionSeconds()).thenReturn(350L);
        runTransactionAndFireAfterCommit(txOps);

        useCase.playerLeavesTheGame();

        verify(gameClockRepositoryOps).saveClock(new GameClock(1_350));
        verify(presenter).presentGameSuspended();
    }

    @Test
    void presentsGameNotInitializedWhenTheClockIsAbsent() {
        // A missing clock is presented and returned before any transaction is opened — not thrown.
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.empty());

        useCase.playerLeavesTheGame();

        verify(presenter).presentGameNotInitialized();
        verify(gameClockRepositoryOps, never()).saveClock(any());
        verify(presenter, never()).presentGameSuspended();
        verify(presenter, never()).presentError(any());
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
    }

    @Test
    void routesAPersistenceFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.of(new GameClock(1_000)));
        when(gameTimeSourceOps.elapsedSessionSeconds()).thenReturn(350L);
        doThrow(boom).when(gameClockRepositoryOps).saveClock(any());
        runTransaction(txOps);

        useCase.playerLeavesTheGame();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameSuspended();
    }
}
