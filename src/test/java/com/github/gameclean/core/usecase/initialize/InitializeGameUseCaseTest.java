package com.github.gameclean.core.usecase.initialize;

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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link InitializeGameUseCase} in isolation — every output port is mocked and the
 * use case is exercised directly through its input port (no Spring, no database). The two phases of the
 * single interaction are covered together: world construction then player placement, plus the
 * precondition that a world which fails to construct stops the interaction before any player is created.
 *
 * <p>The transaction port is stubbed to run its action inline and to fire after-commit callbacks
 * immediately, so the post-commit presentations are observable; the persistence-failure cases instead
 * let the action's error propagate, exactly as the real adapter would. Player-phase cases start from an
 * already-populated world ({@code worldIsEmpty() == false}) so the world step is a quick no-op that
 * still lets player placement proceed.
 */
@ExtendWith(MockitoExtension.class)
class InitializeGameUseCaseTest {

    @Mock
    private InitializeGamePresenterOutputPort presenter;
    @Mock
    private PlayerOperationsOutputPort playerOps;
    @Mock
    private PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    @Mock
    private SceneRepositoryOperationsOutputPort sceneOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private InitializeGameUseCase useCase;

    // --- happy path: world then player ----------------------------------------------------------

    @Test
    void seedsAnEmptyWorldThenCreatesThePlayerAndPresentsBothSuccessesAfterCommit() {
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(scene("scn1")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.initialize(twoConnectedScenes(), "scn1");

        assertScenesSavedInOrder("scn1", "scn2");
        assertSuccessfulConstructionPresentedFor("scn1", "scn2");
        assertPlayerSaved("plr1", "scn1");
        assertSuccessfulCreationPresentedFor("plr1", "scn1");
    }

    @Test
    void skipsSeedingWhenTheWorldIsNotEmptyButStillCreatesThePlayer() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(scene("scn1")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.initialize(twoConnectedScenes(), "scn1");

        verify(sceneOps, never()).saveScene(any());
        verify(presenter).presentWorldAlreadyConstructed();
        assertPlayerSaved("plr1", "scn1");
        verify(presenter).presentSuccessfulPlayerCreation(any());
    }

    // --- a world that fails to construct stops the interaction before any player ----------------

    @Test
    void rejectsAMalformedSceneEntryAndDoesNotCreateAPlayer() {
        // id without the 'scn' prefix — SceneId construction fails the intra-aggregate validity gate
        List<SceneEntry> entries = List.of(
                new SceneEntry("bogus", "Old Gate", "A gate.", "A weathered stone archway.", List.of()));

        useCase.initialize(entries, "scn1");

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNoTransactionAndNoWrites();
        verifyNoPlayerCreated();
    }

    @Test
    void rejectsAnExitWhoseTargetIsNotADefinedSceneAndDoesNotCreateAPlayer() {
        // scn1's only exit points at scn9, which the seed never defines — an inter-aggregate failure
        List<SceneEntry> entries = List.of(
                new SceneEntry("scn1", "Old Gate", "A gate.", "A weathered stone archway.",
                        List.of(new ExitEntry("east", "scn9"))));

        useCase.initialize(entries, "scn1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<SceneId, List<Exit>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentErrorWhenExitTargetUnknown(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys(new SceneId("scn1"));
        assertThat(captor.getValue().get(new SceneId("scn1")))
                .extracting(Exit::getName).containsExactly("east");
        verifyNoTransactionAndNoWrites();
        verifyNoPlayerCreated();
    }

    @Test
    void presentsAnErrorWhenASceneCannotBeSavedAndDoesNotCreateAPlayer() {
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(sceneOps).saveScene(any());
        runTransactionsPropagatingErrors();

        useCase.initialize(twoConnectedScenes(), "scn1");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentSuccessfulWorldConstruction(any());
        verifyNoPlayerCreated();
    }

    // --- player phase (world already usable) ----------------------------------------------------

    @Test
    void rejectsAMalformedPlayerIdBeforeTouchingPlayerPersistence() {
        // 'bogus' lacks the 'plr' prefix — PlayerId construction fails the validity gate.
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("bogus");
        runTransactionsAndFireAfterCommit();

        useCase.initialize(twoConnectedScenes(), "scn1");

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verify(sceneOps, never()).findScene(any());
        verifyNoPlayerCreated();
    }

    @Test
    void rejectsAnUnknownStartingScene() {
        // scn9 is never persisted — an inter-aggregate failure, surfaced before the player transaction.
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(sceneOps.findScene(new SceneId("scn9"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.initialize(twoConnectedScenes(), "scn9");

        verify(presenter).presentStartingSceneUnknown(new SceneId("scn9"));
        verify(playerRepositoryOps, never()).findPlayer(any());
        verifyNoPlayerCreated();
    }

    @Test
    void skipsPlayerCreationWhenAPlayerAlreadyExists() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(scene("scn1")));
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        runTransactionsAndFireAfterCommit();

        useCase.initialize(twoConnectedScenes(), "scn1");

        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(presenter).presentPlayerAlreadyExists(new PlayerId("plr1"));
        verify(presenter, never()).presentSuccessfulPlayerCreation(any());
    }

    @Test
    void presentsAnErrorWhenThePlayerCannotBeSaved() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(sceneOps.findScene(new SceneId("scn1"))).thenReturn(Optional.of(scene("scn1")));
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransactionsPropagatingErrors();

        useCase.initialize(twoConnectedScenes(), "scn1");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentSuccessfulPlayerCreation(any());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static List<SceneEntry> twoConnectedScenes() {
        return List.of(
                new SceneEntry("scn1", "Old Gate", "A weathered archway.",
                        "The gate's iron hinges have long since rusted shut.",
                        List.of(new ExitEntry("east", "scn2"))),
                new SceneEntry("scn2", "Courtyard", "A grass-cracked courtyard.",
                        "Weeds push between the flagstones of a drilling yard.",
                        List.of(new ExitEntry("west", "scn1"))));
    }

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

    private void assertScenesSavedInOrder(String... expectedIds) {
        ArgumentCaptor<Scene> captor = ArgumentCaptor.forClass(Scene.class);
        verify(sceneOps, times(expectedIds.length)).saveScene(captor.capture());
        assertThat(captor.getAllValues()).extracting(scene -> scene.getId().getValue())
                .containsExactly(expectedIds);
    }

    private void assertSuccessfulConstructionPresentedFor(String... expectedIds) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Scene>> captor = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentSuccessfulWorldConstruction(captor.capture());
        assertThat(captor.getValue()).extracting(scene -> scene.getId().getValue())
                .containsExactly(expectedIds);
    }

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

    private void verifyNoTransactionAndNoWrites() {
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        verify(sceneOps, never()).saveScene(any());
    }

    private void verifyNoPlayerCreated() {
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(presenter, never()).presentSuccessfulPlayerCreation(any());
    }
}
