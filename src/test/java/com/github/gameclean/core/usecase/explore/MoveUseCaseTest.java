package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
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
 * Interaction tests for {@link MoveUseCase} in isolation — every output port is mocked and the use case is
 * exercised directly through its input port (no Spring, no database). {@code move} writes, so unlike
 * {@code look} it does collaborate with the transaction port: the stub runs the action inline and fires
 * after-commit callbacks immediately, so the single post-commit presentation of the entered scene is
 * observable; the persistence-failure case instead lets the action's error propagate, as the real adapter
 * would.
 *
 * <p>The interaction presents <em>once</em> on every path: the four not-found branches present and return,
 * the happy path presents the entered scene after commit, and any unhandled failure routes to
 * {@code presentError}.
 */
@ExtendWith(MockitoExtension.class)
class MoveUseCaseTest {

    @Mock
    private MovePresenterOutputPort presenter;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private MoveUseCase useCase;

    @Test
    void movesThePlayerThroughTheExitAndPresentsTheEnteredSceneAfterCommit() {
        Scene courtyard = scene("scn2", "Courtyard");
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(gateTo("scn2")));
        when(sceneOps.findScene(new SceneId("scn2"))).thenReturn(Optional.of(courtyard));
        runTransactionAndFireAfterCommit();

        useCase.playerMovesThrough("east");

        // The player is saved at the target scene...
        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(playerRepositoryOps).savePlayer(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new PlayerId("plr1"));
        assertThat(saved.getValue().getCurrentScene()).isEqualTo(new SceneId("scn2"));
        // ...and the entered scene is presented after the move commits.
        verify(presenter).presentScene(courtyard);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void matchesTheExitCaseInsensitively() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(gateTo("scn2")));
        when(sceneOps.findScene(new SceneId("scn2"))).thenReturn(Optional.of(scene("scn2", "Courtyard")));
        runTransactionAndFireAfterCommit();

        useCase.playerMovesThrough("EAST");

        verify(playerRepositoryOps).savePlayer(any(Player.class));
        verify(presenter).presentScene(any(Scene.class));
    }

    @Test
    void presentsPlayerNotFoundWhenNoPlayerIsPersisted() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());

        useCase.playerMovesThrough("east");

        verify(presenter).presentPlayerNotFound(new PlayerId("plr1"));
        verify(sceneOps, never()).findScene(any());
        verifyNoWriteOrScene();
    }

    @Test
    void presentsCurrentSceneNotFoundWhenThePlayersSceneIsMissing() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn9")));
        when(sceneOps.findScene(new SceneId("scn9"))).thenReturn(Optional.empty());

        useCase.playerMovesThrough("east");

        verify(presenter).presentCurrentSceneNotFound(new SceneId("scn9"));
        verifyNoWriteOrScene();
    }

    @Test
    void presentsNoSuchExitWhenTheCurrentSceneHasNoSuchExit() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(gateTo("scn2")));

        useCase.playerMovesThrough("north");

        verify(presenter).presentNoSuchExit("north");
        // The exit didn't resolve, so the target is never looked up and nothing is written.
        verify(sceneOps, never()).findScene(new SceneId("scn2"));
        verifyNoWriteOrScene();
    }

    @Test
    void presentsTargetSceneNotFoundWhenTheExitLeadsNowhere() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(gateTo("scn2")));
        when(sceneOps.findScene(new SceneId("scn2"))).thenReturn(Optional.empty());

        useCase.playerMovesThrough("east");

        verify(presenter).presentTargetSceneNotFound(new SceneId("scn2"));
        verifyNoWriteOrScene();
    }

    @Test
    void routesAMalformedPlayerIdToTheCatchAll() {
        // 'bogus' lacks the 'plr' prefix — PlayerId construction fails inside the use case.
        when(playerOps.currentPlayerId()).thenReturn("bogus");

        useCase.playerMovesThrough("east");

        verify(presenter).presentError(any(IllegalArgumentException.class));
        verifyNoInteractions(playerRepositoryOps, sceneOps, txOps);
    }

    @Test
    void routesAPersistenceFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(gateTo("scn2")));
        when(sceneOps.findScene(new SceneId("scn2"))).thenReturn(Optional.of(scene("scn2", "Courtyard")));
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransactionPropagatingErrors();

        useCase.playerMovesThrough("east");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentScene(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private static Player player(String id, String currentScene) {
        return Player.builder().id(new PlayerId(id)).currentScene(new SceneId(currentScene)).build();
    }

    /** The player's current scene, with a single "east" exit to the given target. */
    private static Scene gateTo(String targetId) {
        return Scene.builder()
                .id(new SceneId("scn1"))
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of(new Exit("east", new SceneId(targetId))))
                .build();
    }

    private static Scene scene(String id, String name) {
        return Scene.builder()
                .id(new SceneId(id))
                .name(name)
                .shortDescription("A place.")
                .fullDescription("A place worth describing in full.")
                .exits(List.of())
                .build();
    }

    // --- transaction-port stubs ---------------------------------------------------------------------

    /** Run the transactional action inline and fire after-commit callbacks immediately. */
    private void runTransactionAndFireAfterCommit() {
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
    private void runTransactionPropagatingErrors() {
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(anyBoolean(), any(Runnable.class));
    }

    private void verifyNoWriteOrScene() {
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(presenter, never()).presentScene(any());
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
    }
}
