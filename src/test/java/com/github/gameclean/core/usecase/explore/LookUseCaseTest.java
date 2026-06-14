package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link LookUseCase} in isolation — every output port is mocked and the use
 * case is exercised directly through its input port (no Spring, no database). Being a read-only use
 * case, there is no transaction port to stub: the use case reads through the persistence ports and
 * presents directly.
 *
 * <p>The acting player is ambient: {@code playerOps.currentPlayerId()} stands in for "who is acting",
 * and {@code playerLooksAround()} takes no argument. {@code playerRepositoryOps} is the persistence
 * port that loads the player aggregate; the two are distinct ports, hence distinct mocks.
 */
@ExtendWith(MockitoExtension.class)
class LookUseCaseTest {

    @Mock
    private LookPresenterOutputPort presenter;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;

    @InjectMocks
    private LookUseCase useCase;

    @Test
    void presentsThePlayersCurrentScene() {
        Scene oldGate = scene("scn1");
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(oldGate));

        useCase.playerLooksAround();

        verify(presenter).presentScene(oldGate);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void presentsPlayerNotFoundWhenNoPlayerIsPersisted() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());

        useCase.playerLooksAround();

        verify(presenter).presentPlayerNotFound(new PlayerId("plr1"));
        verify(sceneOps, never()).findScene(any());
        verify(presenter, never()).presentScene(any());
    }

    @Test
    void presentsCurrentSceneNotFoundWhenThePlayersSceneIsMissing() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn9")));
        when(sceneOps.findScene(new SceneId("scn9"))).thenReturn(Optional.empty());

        useCase.playerLooksAround();

        verify(presenter).presentCurrentSceneNotFound(new SceneId("scn9"));
        verify(presenter, never()).presentScene(any());
    }

    @Test
    void routesAMalformedPlayerIdToTheCatchAll() {
        // 'bogus' lacks the 'plr' prefix — PlayerId construction fails inside the use case.
        when(playerOps.currentPlayerId()).thenReturn("bogus");

        useCase.playerLooksAround();

        verify(presenter).presentError(any(IllegalArgumentException.class));
        verifyNoInteractions(playerRepositoryOps, sceneOps);
    }

    @Test
    void routesAPersistenceFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenThrow(boom);

        useCase.playerLooksAround();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentScene(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

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
}
