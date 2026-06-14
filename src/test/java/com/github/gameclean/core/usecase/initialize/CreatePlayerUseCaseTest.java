package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link CreatePlayerUseCase} in isolation — every output port is mocked and the
 * use case is exercised directly through its input port (no Spring, no database). The transaction port
 * is stubbed to run its action inline and to fire after-commit callbacks immediately, so the
 * post-commit presentation is observable; the persistence-failure case instead lets the action's error
 * propagate, exactly as the real adapter would.
 *
 * <p>The acting player's id is ambient: {@code playerOps.currentPlayerId()} stands in for "who is
 * acting", while the starting scene crosses as the {@code createPlayer} argument. The two persistence
 * ports ({@code sceneOps} for the inter-aggregate scene check, {@code playerRepositoryOps} for the
 * existence guard and write) are distinct, hence distinct mocks.
 */
@ExtendWith(MockitoExtension.class)
class CreatePlayerUseCaseTest {

    @Mock
    private CreatePlayerPresenterOutputPort presenter;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private CreatePlayerUseCase useCase;

    @Test
    void createsThePlayerAndPresentsSuccessAfterCommit() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(scene("scn1")));
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.createPlayer("scn1");

        assertPlayerSaved("plr1", "scn1");
        assertSuccessfulCreationPresentedFor("plr1", "scn1");
    }

    @Test
    void skipsCreationAndPresentsAlreadyExistsWhenAPlayerIsPresent() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(scene("scn1")));
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        runTransactionsAndFireAfterCommit();

        useCase.createPlayer("scn1");

        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(presenter).presentPlayerAlreadyExists(new PlayerId("plr1"));
        verify(presenter, never()).presentSuccessfulPlayerCreation(any());
    }

    @Test
    void rejectsAMalformedPlayerIdBeforeTouchingPersistence() {
        // 'bogus' lacks the 'plr' prefix — PlayerId construction fails the validity gate.
        when(playerOps.currentPlayerId()).thenReturn("bogus");

        useCase.createPlayer("scn1");

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNoInteractions(sceneOps, playerRepositoryOps, txOps);
    }

    @Test
    void rejectsAnUnknownStartingScene() {
        // scn9 is never persisted — an inter-aggregate failure, surfaced before any transaction.
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(sceneOps.findScene(new SceneId("scn9"))).thenReturn(Optional.empty());

        useCase.createPlayer("scn9");

        verify(presenter).presentStartingSceneUnknown(new SceneId("scn9"));
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(presenter, never()).presentSuccessfulPlayerCreation(any());
    }

    @Test
    void presentsAnErrorWhenThePlayerCannotBeSaved() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(scene("scn1")));
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransactionsPropagatingErrors();

        useCase.createPlayer("scn1");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentSuccessfulPlayerCreation(any());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static Player player(String id, String currentScene) {
        return Player.builder().id(new PlayerId(id)).currentScene(new SceneId(currentScene)).build();
    }

    private static Scene scene(String id) {
        return Scene.builder()
                .id(new SceneId(id))
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of())
                .build();
    }

    // --- transaction-port stubs -----------------------------------------------------------------

    /** Run the transactional action inline and fire after-commit callbacks immediately. */
    private void runTransactionsAndFireAfterCommit() {
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(anyBoolean(), any(Runnable.class));
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(txOps).doAfterCommit(any(Runnable.class));
    }

    /** Run the transactional action inline, letting any error it throws propagate (as the real port does). */
    private void runTransactionsPropagatingErrors() {
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(anyBoolean(), any(Runnable.class));
    }

    // --- assertion helpers ----------------------------------------------------------------------

    private void assertPlayerSaved(String expectedId, String expectedScene) {
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepositoryOps).savePlayer(captor.capture());
        assertThat(captor.getValue().getId().getValue()).isEqualTo(expectedId);
        assertThat(captor.getValue().getCurrentScene().getValue()).isEqualTo(expectedScene);
    }

    private void assertSuccessfulCreationPresentedFor(String expectedId, String expectedScene) {
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(presenter).presentSuccessfulPlayerCreation(captor.capture());
        assertThat(captor.getValue().getId().getValue()).isEqualTo(expectedId);
        assertThat(captor.getValue().getCurrentScene().getValue()).isEqualTo(expectedScene);
    }
}
