package com.github.gameclean.core.usecase.orient;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for the {@link OrientPlayerSubcase} in isolation — every output port is mocked and the
 * subcase is exercised directly. The guarded prologue has two kinds of exit and the tests pin both: the
 * success branch <em>returns</em> the oriented player and scene and presents nothing; each failure branch
 * <em>presents</em> the outcome and throws {@link SubcaseAlreadyPresented}. Unexpected failures (a
 * malformed id, a persistence fault) propagate with no presentation, for the parent use case to route to
 * its catch-all.
 */
@ExtendWith(MockitoExtension.class)
class OrientPlayerSubcaseTest {

    @Mock
    private OrientPlayerPresenterOutputPort presenter;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;

    @InjectMocks
    private OrientPlayerSubcase subcase;

    @Test
    void returnsTheOrientedPlayerAndSceneWithoutPresentingOnSuccess() {
        Player plr = player("plr1", "scn1");
        Scene oldGate = scene("scn1");
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.of(plr));
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(oldGate));

        OrientPlayerResult result = subcase.playerGetsBearings();

        assertThat(result.getPlayer()).isEqualTo(plr);
        assertThat(result.getScene()).isEqualTo(oldGate);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsPlayerNotFoundAndSignalsWhenNoPlayerIsPersisted() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());

        assertThatThrownBy(subcase::playerGetsBearings).isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentPlayerNotFound(new PlayerId("plr1"));
        verify(sceneOps, never()).findScene(any());
    }

    @Test
    void presentsCurrentSceneNotFoundAndSignalsWhenThePlayersSceneIsMissing() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn9")));
        when(sceneOps.findScene(new SceneId("scn9"))).thenReturn(Optional.empty());

        assertThatThrownBy(subcase::playerGetsBearings).isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentCurrentSceneNotFound(new SceneId("scn9"));
    }

    @Test
    void propagatesAMalformedPlayerIdWithoutPresenting() {
        // 'bogus' lacks the 'plr' prefix — PlayerId construction fails the validity gate.
        when(playerOps.currentPlayerId()).thenReturn("bogus");

        assertThatThrownBy(subcase::playerGetsBearings).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(presenter, playerRepositoryOps, sceneOps);
    }

    @Test
    void propagatesAPersistenceFailureWithoutPresenting() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenThrow(boom);

        assertThatThrownBy(subcase::playerGetsBearings).isSameAs(boom);

        verifyNoInteractions(presenter);
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
