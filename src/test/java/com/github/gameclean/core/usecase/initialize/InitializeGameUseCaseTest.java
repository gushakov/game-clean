package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.InvalidDomainObjectError;
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
import com.github.gameclean.core.port.seed.ExitEntry;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsError;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.core.port.seed.SceneEntry;
import com.github.gameclean.core.port.seed.SpawnEntry;
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
 * <p>The use case <em>pulls</em> its seed, so every test stubs {@code seedSourceOps.loadGameSeed()} with the
 * authored fixture and then fires the no-argument {@code systemInitializesGame()}. A seed-source failure is
 * presented like any other infrastructure fault, via the outermost checkpoint.
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
    private GameSeedSourceOperationsOutputPort seedSourceOps;
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
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame();

        assertScenesSavedInOrder("scn1", "scn2");
        assertPlayerSaved("plr1", "scn1");
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    @Test
    void addsThePlayerToAnAlreadySeededWorldAndPresentsGameInitialized() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame();

        verify(sceneOps, never()).saveScene(any());
        assertPlayerSaved("plr1", "scn1");
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    @Test
    void writesNothingButStillPresentsGameInitializedWhenWorldAndPlayerAlreadyExist() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame();

        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    // --- item spawning --------------------------------------------------------------------------

    @Test
    void spawnsItemsByTheRollsAndPresentsThem() {
        givenSeed(seed(twoConnectedScenes(), "scn1", item("itm1", 1, 1, 1, "scn1", "scn2")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        // One item over two candidate scenes, always-hit chance, one try: hit (0.0 < 1), then pick draw
        // 0.6 selects candidate index (int)(0.6 * 2) = 1, i.e. scn2.
        when(randomnessOps.nextDouble()).thenReturn(0.0, 0.6);
        when(idGeneratorOps.generateItemId()).thenReturn(new ItemId("itmAAA"));
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame();

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
        givenSeed(seed(twoConnectedScenes(), "scn1", item("itm1", 1, 1, 1, "scn1")));
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(itemOps.itemsAlreadySpawned()).thenReturn(true);
        // The rolls still happen (outside the transaction), but the guard means nothing is saved.
        when(randomnessOps.nextDouble()).thenReturn(0.0, 0.0);
        when(idGeneratorOps.generateItemId()).thenReturn(new ItemId("itmAAA"));
        runTransactionsAndFireAfterCommit();

        useCase.systemInitializesGame();

        verify(itemOps, never()).saveItem(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> presentedItems = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(anyList(), eq(new PlayerId("plr1")), presentedItems.capture());
        assertThat(presentedItems.getValue()).isEmpty();
    }

    @Test
    void rejectsAnItemWithAnInvalidChanceAndDoesNotInitialize() {
        // Denominator 0 — Chance construction fails the intra-aggregate validity gate.
        givenSeed(seed(twoConnectedScenes(), "scn1", item("itm1", 1, 0, 1, "scn1")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnItemSpawningIntoAnUnknownSceneAndDoesNotInitialize() {
        // scn9 is a well-formed id but no authored scene defines it — an inter-aggregate failure.
        givenSeed(seed(twoConnectedScenes(), "scn1", item("itm1", 1, 2, 1, "scn9")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

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
        givenSeed(seed(entries, "scn1"));

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnExitWhoseTargetIsNotADefinedSceneAndDoesNotInitialize() {
        // scn1's only exit points at scn9, which the seed never defines — an inter-aggregate failure
        List<SceneEntry> entries = List.of(
                new SceneEntry("scn1", "Old Gate", "A gate.", "A weathered stone archway.",
                        List.of(new ExitEntry("east", "scn9"))));
        givenSeed(seed(entries, "scn1"));

        useCase.systemInitializesGame();

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
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(playerOps.currentPlayerId()).thenReturn("bogus");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAStartingSceneNotAmongTheAuthoredScenes() {
        // scn9 is a well-formed id but no authored scene defines it — an inter-aggregate failure,
        // resolved against the in-memory world rather than the (as-yet-unseeded) store.
        givenSeed(seed(twoConnectedScenes(), "scn9"));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentStartingSceneUnknown(new SceneId("scn9"));
        verifyNothingInitialized();
    }

    // --- a seed-source failure is presented like any infrastructure fault -----------------------

    @Test
    void presentsAnErrorWhenTheSeedCannotBeLoaded() {
        GameSeedSourceOperationsError boom = new GameSeedSourceOperationsError("seed resource missing");
        when(seedSourceOps.loadGameSeed()).thenThrow(boom);

        useCase.systemInitializesGame();

        verify(presenter).presentError(boom);
        verifyNothingInitialized();
    }

    // --- a persistence failure inside the single transaction routes to the catch-all ------------

    @Test
    void presentsAnErrorWhenASceneCannotBeSaved() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(sceneOps).saveScene(any());
        runTransactionsPropagatingErrors();

        useCase.systemInitializesGame();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any(), any());
    }

    @Test
    void presentsAnErrorWhenThePlayerCannotBeSaved() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(new PlayerId("plr1"))).thenReturn(Optional.empty());
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransactionsPropagatingErrors();

        useCase.systemInitializesGame();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any(), any());
    }

    // --- fixtures -------------------------------------------------------------------------------

    /** Stub the source port to yield the given authored seed — the use case pulls it as Checkpoint 1. */
    private void givenSeed(GameSeed gameSeed) {
        when(seedSourceOps.loadGameSeed()).thenReturn(gameSeed);
    }

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
