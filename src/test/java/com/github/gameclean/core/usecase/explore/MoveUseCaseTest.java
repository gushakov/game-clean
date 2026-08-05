package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransaction;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransactionAndFireAfterCommit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link MoveUseCase} in isolation — every output port is mocked and the use case is
 * exercised directly through its input port. The shared opening is delegated to the
 * {@link OrientPlayerSubcaseInputPort orient subcase}, so it is mocked: it returns the player and their
 * current scene on the happy paths, or signals {@link SubcaseAlreadyPresented} when it has already presented
 * a not-found outcome (covered in detail by {@code OrientPlayerSubcaseTest}). {@code move} writes, so unlike
 * {@code look} it collaborates with the transaction port: the stub runs the action inline and fires
 * after-commit callbacks immediately, making the single post-commit presentation observable; the
 * persistence-failure case instead lets the action's error propagate, as the real adapter would.
 *
 * <p>The interaction presents <em>once</em> on every path: the subcase presents the two not-found outcomes;
 * the no-such-exit and dangling-target branches present and return; the happy path presents the entered
 * scene after commit; any unhandled failure routes to {@code presentError}.
 */
@ExtendWith(MockitoExtension.class)
class MoveUseCaseTest {

    @Mock
    private MovePresenterOutputPort presenter;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;
    @Mock
    private NpcRepositoryOperationsOutputPort npcOps;
    @Mock
    private TransactionOperationsOutputPort txOps;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;

    @InjectMocks
    private MoveUseCase useCase;

    @Test
    void movesThePlayerThroughTheExitAndPresentsTheEnteredSceneAfterCommit() {
        Scene courtyard = scene("scn2", "Courtyard");
        List<Item> itemsInCourtyard = List.of(item("itm1", "scn2", "A rusty dagger."));
        List<Npc> npcsInCourtyard = List.of(npc("npc1", "scn2"));
        orientedAt("plr1", gateTo("scn2"));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(courtyard));
        when(itemOps.findItemsInScene(SceneId.of("scn2"))).thenReturn(itemsInCourtyard);
        when(npcOps.findNpcsInScene(SceneId.of("scn2"))).thenReturn(npcsInCourtyard);
        runTransactionAndFireAfterCommit(txOps);

        useCase.playerMovesThrough("east");

        // The player is saved at the target scene...
        ArgumentCaptor<Player> saved = ArgumentCaptor.forClass(Player.class);
        verify(playerRepositoryOps).savePlayer(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(PlayerId.of("plr1"));
        assertThat(saved.getValue().getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        // ...and the entered scene is presented after the move commits, with the items and NPCs in it.
        verify(presenter).presentSceneEntered(courtyard, itemsInCourtyard, npcsInCourtyard);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void matchesTheExitCaseInsensitively() {
        orientedAt("plr1", gateTo("scn2"));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(scene("scn2", "Courtyard")));
        runTransactionAndFireAfterCommit(txOps);

        useCase.playerMovesThrough("EAST");

        verify(playerRepositoryOps).savePlayer(any(Player.class));
        verify(presenter).presentSceneEntered(any(Scene.class), anyList(), anyList());
    }

    @Test
    void presentsNothingWhenTheSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerMovesThrough("east");

        verifyNoInteractions(presenter, playerRepositoryOps, sceneOps, itemOps, npcOps, txOps);
    }

    @Test
    void presentsNoSuchExitWhenTheCurrentSceneHasNoSuchExit() {
        orientedAt("plr1", gateTo("scn2"));

        useCase.playerMovesThrough("north");

        verify(presenter).presentNoSuchExit("north");
        // The exit didn't resolve, so the target is never looked up and nothing is written.
        verify(sceneOps, never()).findScene(any());
        verifyNoWriteOrScene();
    }

    @Test
    void presentsTargetSceneNotFoundWhenTheExitLeadsNowhere() {
        orientedAt("plr1", gateTo("scn2"));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.empty());

        useCase.playerMovesThrough("east");

        verify(presenter).presentTargetSceneNotFound(SceneId.of("scn2"));
        verifyNoWriteOrScene();
    }

    @Test
    void routesAnUnexpectedSubcaseFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(boom);

        useCase.playerMovesThrough("east");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentSceneEntered(any(), any(), any());
        verifyNoWriteOrScene();
    }

    @Test
    void routesAPersistenceFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        orientedAt("plr1", gateTo("scn2"));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(scene("scn2", "Courtyard")));
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransaction(txOps);

        useCase.playerMovesThrough("east");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentSceneEntered(any(), any(), any());
    }

    @Test
    void routesAnOptimisticLockLossToTheCatchAll() {
        // move now races the NPC counterstrike for the player row, but stays on the PLAIN doInTransaction
        // overload — so a lost lock is NOT reacted to locally: it propagates like any other error to the
        // outermost catch and presentError (the single-writer→propagate vs contested→handler contrast).
        OptimisticLockingError lost = new OptimisticLockingError("player modified concurrently");
        orientedAt("plr1", gateTo("scn2"));
        when(sceneOps.findScene(SceneId.of("scn2"))).thenReturn(Optional.of(scene("scn2", "Courtyard")));
        doThrow(lost).when(playerRepositoryOps).savePlayer(any());
        runTransaction(txOps);

        useCase.playerMovesThrough("east");

        verify(presenter).presentError(lost);
        verify(presenter, never()).presentSceneEntered(any(), any(), any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    /** Stub the orient subcase to return the player standing in the given current scene. */
    private void orientedAt(String playerId, Scene currentScene) {
        when(orientPlayerSubcase.playerGetsBearings())
                .thenReturn(new OrientPlayerResult(
                        player(playerId, currentScene.getId().asString()), currentScene));
    }

    private static Player player(String id, String currentScene) {
        return Player.builder().id(PlayerId.of(id)).currentScene(SceneId.of(currentScene))
                .hitPoints(HitPoints.full(30)).version(1).build();
    }

    /** The player's current scene (scn1), with a single "east" exit to the given target. */
    private static Scene gateTo(String targetId) {
        return Scene.builder()
                .id(SceneId.of("scn1"))
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of(new Exit("east", SceneId.of(targetId))))
                .build();
    }

    private static Scene scene(String id, String name) {
        return Scene.builder()
                .id(SceneId.of(id))
                .name(name)
                .shortDescription("A place.")
                .fullDescription("A place worth describing in full.")
                .exits(List.of())
                .build();
    }

    private void verifyNoWriteOrScene() {
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(presenter, never()).presentSceneEntered(any(), any(), any());
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
    }

    private static Item item(String id, String scene, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.OnGround(SceneId.of(scene)))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .build();
    }

    private static Npc npc(String id, String scene) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(SceneId.of(scene))
                .shortDescription("A hooded wanderer.")
                .fullDescription("A cloaked figure.")
                .moveChance(new Chance(1, 4))
                .attackChance(new Chance(1, 3))
                .hitPoints(HitPoints.full(10))
                .build();
    }
}
