package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.id.IdGeneratorOperationsOutputPort;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.randomness.RandomnessOperationsOutputPort;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link InitializeGameUseCase} in isolation — every output port is mocked and the
 * use case is exercised directly through its input port (no Spring, no database). The three phases of the
 * single interaction are covered together: world construction, player placement and item spawning, plus the
 * precondition that a world which fails to construct stops the interaction before any player or item.
 *
 * <p>The interaction presents <em>once</em>: every happy combination (world seeded or already present,
 * player created or already present, items spawned or already spawned) ends in the single
 * {@code presentGameInitialized} success, so the tests assert that one outcome rather than per-phase
 * presentations. Spawning is non-deterministic, so the randomness port is stubbed with a fixed sequence of
 * draws and the id generator with fixed ids, making placements reproducible. The transaction port is stubbed
 * to run its action inline and fire after-commit callbacks immediately; the persistence-failure cases instead
 * let the action's error propagate, exactly as the real adapter would.
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
    private ItemRepositoryOperationsOutputPort itemOps;
    @Mock
    private IdGeneratorOperationsOutputPort idGeneratorOps;
    @Mock
    private RandomnessOperationsOutputPort randomnessOps;
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

        useCase.systemInitializesGame(seed(twoConnectedScenes(), "scn1"));

        assertScenesSavedInOrder("scn1", "scn2");
        assertPlayerSaved("plr1", "scn1");
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    @Test
    void addsThePlayerToAnAlreadySeededWorldAndPresentsGameInitialized() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame(seed(twoConnectedScenes(), "scn1"));

        verify(sceneOps, never()).saveScene(any());
        assertPlayerSaved("plr1", "scn1");
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    @Test
    void writesNothingButStillPresentsGameInitializedWhenWorldAndPlayerAlreadyExist() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame(seed(twoConnectedScenes(), "scn1"));

        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    // --- item spawning --------------------------------------------------------------------------

    @Test
    void spawnsItemsByTheRollsAndPresentsThem() {
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        // One item over two candidate scenes, always-hit chance, one try: hit (0.0 < 1), then pick draw
        // 0.6 selects candidate index (int)(0.6 * 2) = 1, i.e. scn2.
        when(randomnessOps.nextDouble()).thenReturn(0.0, 0.6);
        when(idGeneratorOps.generateItemId()).thenReturn(new ItemId("itmAAA"));
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame(
                seed(twoConnectedScenes(), "scn1", item("itm1", 1, 1, 1, "scn1", "scn2")));

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new ItemId("itmAAA"));
        assertThat(saved.getValue().getLocation()).isEqualTo(new SceneId("scn2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> presentedItems = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(anyList(), eq(new PlayerId("plr1")), presentedItems.capture());
        assertThat(presentedItems.getValue()).extracting(i -> i.getId().getValue()).containsExactly("itmAAA");
    }

    @Test
    void doesNotReSpawnItemsWhenItemsAlreadyExist() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(itemOps.itemsAlreadySpawned()).thenReturn(true);
        // The rolls still happen (outside the transaction), but the guard means nothing is saved.
        when(randomnessOps.nextDouble()).thenReturn(0.0, 0.0);
        when(idGeneratorOps.generateItemId()).thenReturn(new ItemId("itmAAA"));
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame(
                seed(twoConnectedScenes(), "scn1", item("itm1", 1, 1, 1, "scn1")));

        verify(itemOps, never()).saveItem(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> presentedItems = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(anyList(), eq(new PlayerId("plr1")), presentedItems.capture());
        assertThat(presentedItems.getValue()).isEmpty();
    }

    @Test
    void rejectsAnItemWithAnInvalidChanceAndDoesNotInitialize() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        // Denominator 0 — Chance construction fails the intra-aggregate validity gate.
        useCase.systemInitializesGame(
                seed(twoConnectedScenes(), "scn1", item("itm1", 1, 0, 1, "scn1")));

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnItemSpawningIntoAnUnknownSceneAndDoesNotInitialize() {
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        // scn9 is a well-formed id but no authored scene defines it — an inter-aggregate failure.
        useCase.systemInitializesGame(
                seed(twoConnectedScenes(), "scn1", item("itm1", 1, 2, 1, "scn9")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<SceneId>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentItemSpawnSceneUnknown(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("itm1");
        assertThat(captor.getValue().get("itm1")).containsExactly(new SceneId("scn9"));
        verifyNothingInitialized();
    }

    // --- a world that fails to construct stops the interaction before any player ----------------

    @Test
    void rejectsAMalformedSceneEntryAndDoesNotInitialize() {
        // id without the 'scn' prefix — SceneId construction fails the intra-aggregate validity gate
        List<SceneEntry> entries = List.of(
                new SceneEntry("bogus", "Old Gate", "A gate.", "A weathered stone archway.", List.of()));

        useCase.systemInitializesGame(seed(entries, "scn1"));

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnExitWhoseTargetIsNotADefinedSceneAndDoesNotInitialize() {
        // scn1's only exit points at scn9, which the seed never defines — an inter-aggregate failure
        List<SceneEntry> entries = List.of(
                new SceneEntry("scn1", "Old Gate", "A gate.", "A weathered stone archway.",
                        List.of(new ExitEntry("east", "scn9"))));

        useCase.systemInitializesGame(seed(entries, "scn1"));

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

        useCase.systemInitializesGame(seed(twoConnectedScenes(), "scn1"));

        verify(presenter).presentInvalidParametersError(any(IllegalArgumentException.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAStartingSceneNotAmongTheAuthoredScenes() {
        // scn9 is a well-formed id but no authored scene defines it — an inter-aggregate failure,
        // resolved against the in-memory world rather than the (as-yet-unseeded) store.
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame(seed(twoConnectedScenes(), "scn9"));

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

        useCase.systemInitializesGame(seed(twoConnectedScenes(), "scn1"));

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any(), any());
    }

    @Test
    void presentsAnErrorWhenThePlayerCannotBeSaved() {
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransactionsPropagatingErrors();

        useCase.systemInitializesGame(seed(twoConnectedScenes(), "scn1"));

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any(), any());
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static GameSeed seed(List<SceneEntry> scenes, String startingSceneId, ItemEntry... items) {
        return new GameSeed(scenes, startingSceneId, List.of(items));
    }

    private static List<SceneEntry> twoConnectedScenes() {
        return List.of(
                new SceneEntry("scn1", "Old Gate", "A weathered archway.",
                        "The gate's iron hinges have long since rusted shut.",
                        List.of(new ExitEntry("east", "scn2"))),
                new SceneEntry("scn2", "Courtyard", "A grass-cracked courtyard.",
                        "Weeds push between the flagstones of a drilling yard.",
                        List.of(new ExitEntry("west", "scn1"))));
    }

    private static ItemEntry item(String id, int chanceNumerator, int chanceDenominator, int max,
                                  String... candidateScenes) {
        return new ItemEntry(id, "A rusty dagger.", "A plain iron dagger, rusty but usable.",
                new SpawnEntry(List.of(candidateScenes), chanceNumerator, chanceDenominator, max));
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

    private void assertGameInitializedPresentedWithNoItems(String expectedPlayerId, String... expectedSceneIds) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Scene>> scenesCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(
                scenesCaptor.capture(), eq(new PlayerId(expectedPlayerId)), itemsCaptor.capture());
        assertThat(scenesCaptor.getValue()).extracting(scene -> scene.getId().getValue())
                .containsExactly(expectedSceneIds);
        assertThat(itemsCaptor.getValue()).isEmpty();
    }

    private void verifyNothingInitialized() {
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(itemOps, never()).saveItem(any());
        verify(presenter, never()).presentGameInitialized(any(), any(), any());
    }
}
