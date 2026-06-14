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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link InitializeGameUseCase} in isolation — every output port is mocked and the
 * use case is exercised directly through its input port (no Spring, no database). The two phases of the
 * single interaction are covered together: world construction then player placement, plus the
 * precondition that a world which fails to construct stops the interaction before any player is created.
 *
 * <p>The interaction presents <em>once</em>: every happy combination (world seeded or already present,
 * player created or already present) ends in the single {@code presentGameInitialized} success, so the
 * tests assert that one outcome rather than per-phase presentations. The transaction port is stubbed to
 * run its action inline and to fire after-commit callbacks immediately, so the single post-commit
 * presentation is observable; the persistence-failure cases instead let the action's error propagate,
 * exactly as the real adapter would.
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

    // --- the single success outcome, across the idempotency branches ----------------------------

    @Test
    void seedsAnEmptyWorldCreatesThePlayerAndPresentsGameInitializedAfterCommit() {
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame(twoConnectedScenes(), "scn1");

        assertScenesSavedInOrder("scn1", "scn2");
        assertPlayerSaved("plr1", "scn1");
        assertGameInitializedPresentedFor("plr1", "scn1", "scn2");
    }

    @Test
    void addsThePlayerToAnAlreadySeededWorldAndPresentsGameInitialized() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame(twoConnectedScenes(), "scn1");

        verify(sceneOps, never()).saveScene(any());
        assertPlayerSaved("plr1", "scn1");
        assertGameInitializedPresentedFor("plr1", "scn1", "scn2");
    }

    @Test
    void writesNothingButStillPresentsGameInitializedWhenWorldAndPlayerAlreadyExist() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame(twoConnectedScenes(), "scn1");

        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        assertGameInitializedPresentedFor("plr1", "scn1", "scn2");
    }

    // --- a world that fails to construct stops the interaction before any player ----------------

    @Test
    void rejectsAMalformedSceneEntryAndDoesNotInitialize() {
        // id without the 'scn' prefix — SceneId construction fails the intra-aggregate validity gate
        List<SceneEntry> entries = List.of(
                new SceneEntry("bogus", "Old Gate", "A gate.", "A weathered stone archway.", List.of()));

        useCase.systemInitializesGame(entries, "scn1");

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnExitWhoseTargetIsNotADefinedSceneAndDoesNotInitialize() {
        // scn1's only exit points at scn9, which the seed never defines — an inter-aggregate failure
        List<SceneEntry> entries = List.of(
                new SceneEntry("scn1", "Old Gate", "A gate.", "A weathered stone archway.",
                        List.of(new ExitEntry("east", "scn9"))));

        useCase.systemInitializesGame(entries, "scn1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<SceneId, List<Exit>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentErrorWhenExitTargetUnknown(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys(new SceneId("scn1"));
        assertThat(captor.getValue().get(new SceneId("scn1")))
                .extracting(Exit::getName).containsExactly("east");
        verifyNothingInitialized();
    }

    @Test
    void rejectsAMalformedPlayerIdAndDoesNotInitialize() {
        // 'bogus' lacks the 'plr' prefix — PlayerId construction fails the validity gate.
        when(playerOps.currentPlayerId()).thenReturn("bogus");

        useCase.systemInitializesGame(twoConnectedScenes(), "scn1");

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAStartingSceneNotAmongTheAuthoredScenes() {
        // scn9 is a well-formed id but no authored scene defines it — an inter-aggregate failure,
        // resolved against the in-memory world rather than the (as-yet-unseeded) store.
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame(twoConnectedScenes(), "scn9");

        verify(presenter).presentStartingSceneUnknown(new SceneId("scn9"));
        verifyNothingInitialized();
    }

    // --- a persistence failure inside the single transaction routes to the catch-all ------------

    @Test
    void presentsAnErrorWhenASceneCannotBeSaved() {
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(sceneOps).saveScene(any());
        runTransactionsPropagatingErrors();

        useCase.systemInitializesGame(twoConnectedScenes(), "scn1");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any());
    }

    @Test
    void presentsAnErrorWhenThePlayerCannotBeSaved() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransactionsPropagatingErrors();

        useCase.systemInitializesGame(twoConnectedScenes(), "scn1");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any());
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

    private void assertPlayerSaved(String expectedId, String expectedScene) {
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepositoryOps).savePlayer(captor.capture());
        assertThat(captor.getValue().getId().getValue()).isEqualTo(expectedId);
        assertThat(captor.getValue().getCurrentScene().getValue()).isEqualTo(expectedScene);
    }

    private void assertGameInitializedPresentedFor(String expectedPlayerId, String... expectedSceneIds) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Scene>> captor = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(captor.capture(), eq(new PlayerId(expectedPlayerId)));
        assertThat(captor.getValue()).extracting(scene -> scene.getId().getValue())
                .containsExactly(expectedSceneIds);
    }

    private void verifyNothingInitialized() {
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(presenter, never()).presentGameInitialized(any(), any());
    }
}
