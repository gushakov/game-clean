package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.daytime.DayPhaseLog;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.npc.Offer;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Lock;
import com.github.gameclean.core.model.scene.MiniGame;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.DayPhaseLogRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.seed.ContainsEntry;
import com.github.gameclean.core.port.seed.ExitEntry;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsError;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.core.port.seed.NpcEntry;
import com.github.gameclean.core.port.seed.OfferEntry;
import com.github.gameclean.core.port.seed.SceneEntry;
import com.github.gameclean.core.port.seed.SpawnEntry;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransactionWithResult;
import static org.mockito.ArgumentMatchers.any;
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
 * presentations. Spawning is non-deterministic, so the {@link ScriptedDice} is scripted with fixed rolls and
 * picks — the scene pick followed by the id-glyph picks the model now mints each {@code ItemId} from — making
 * both placements and ids reproducible. The transaction port is stubbed to run its action inline and fire
 * after-commit callbacks immediately; the persistence-failure cases instead let the action's error propagate,
 * exactly as the real adapter would.
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
    private NpcRepositoryOperationsOutputPort npcOps;
    @Mock
    private GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    @Mock
    private DayPhaseLogRepositoryOperationsOutputPort dayPhaseLogRepositoryOps;
    @Spy
    private ScriptedDice dice = new ScriptedDice();
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
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        runTransactionWithResult(txOps);

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
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        runTransactionWithResult(txOps);

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
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.of(GameClock.initial()));
        when(dayPhaseLogRepositoryOps.findDayPhaseLog()).thenReturn(Optional.of(DayPhaseLog.initial()));
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(gameClockRepositoryOps, never()).saveClock(any());
        verify(dayPhaseLogRepositoryOps, never()).saveDayPhaseLog(any());
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    @Test
    void seedsTheClockAtZeroWhenAbsentDuringInitialization() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // The clock is absent (default Optional.empty), so it is created at time zero.
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(gameClockRepositoryOps).saveClock(GameClock.initial());
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    @Test
    void seedsTheDayPhaseLogAtTheSentinelWhenAbsentDuringInitialization() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // The day-phase log is absent (default Optional.empty), so it is created at the "nothing announced" sentinel.
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(dayPhaseLogRepositoryOps).saveDayPhaseLog(DayPhaseLog.initial());
        assertGameInitializedPresentedWithNoItems("plr1", "scn1", "scn2");
    }

    // --- item spawning --------------------------------------------------------------------------

    @Test
    void spawnsItemsByTheRollsAndPresentsThem() {
        givenSeed(seed(twoConnectedScenes(), "scn1", item("itm1", 1, 1, 1, "scn1", "scn2")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // One item over two candidate scenes, always-hit chance, one try: roll hits, pick scene index 1
        // (scn2), then mint the id by picking 8 alphabet glyphs — all index 0 ('0') -> "itm00000000".
        dice.willRoll(true).willPick(1, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(ItemId.of("itm00000000"));
        assertThat(saved.getValue().getLocation()).isEqualTo(new Location.OnGround(SceneId.of("scn2")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> presentedItems = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(
                anyList(), eq(PlayerId.of("plr1")), presentedItems.capture(), anyList());
        assertThat(presentedItems.getValue()).extracting(i -> i.getId().asString()).containsExactly("itm00000000");
    }

    @Test
    void doesNotReSpawnItemsWhenItemsAlreadyExist() {
        givenSeed(seed(twoConnectedScenes(), "scn1", item("itm1", 1, 1, 1, "scn1")));
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(itemOps.itemsAlreadySpawned()).thenReturn(true);
        // The rolls still happen (outside the transaction) — scene pick + 8 id glyphs — but the guard means
        // nothing is saved.
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(itemOps, never()).saveItem(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> presentedItems = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(
                anyList(), eq(PlayerId.of("plr1")), presentedItems.capture(), anyList());
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
        assertThat(captor.getValue().get("itm1")).containsExactly(SceneId.of("scn9"));
        verifyNothingInitialized();
    }

    // --- containment ----------------------------------------------------------------------------

    @Test
    void fillsASpawnedContainerFromItsAuthoredContainmentAndPresentsBoth() {
        givenSeed(seed(twoConnectedScenes(), "scn1",
                containedOnlyItem("itm1"),
                containerItem("itm4", List.of(new ContainsEntry("itm1", 1, 1)), 1, 1, 1, "scn1")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // The contained-only itm1 draws nothing of its own. The chest: spawn roll hits, scene pick 0 (scn1),
        // 8 zero-glyphs -> "itm00000000"; then its one containment entry rolls a hit and mints the contained
        // instance from 8 one-glyphs -> "itm11111111".
        dice.willRoll(true, true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps, times(2)).saveItem(saved.capture());
        Item chest = saved.getAllValues().get(0);
        Item dagger = saved.getAllValues().get(1);
        assertThat(chest.getId()).isEqualTo(ItemId.of("itm00000000"));
        assertThat(chest.isContainer()).isTrue();
        assertThat(chest.getLocation()).isEqualTo(new Location.OnGround(SceneId.of("scn1")));
        assertThat(dagger.getId()).isEqualTo(ItemId.of("itm11111111"));
        assertThat(dagger.isContainer()).isFalse();
        assertThat(dagger.getLocation()).isEqualTo(new Location.Inside(chest.getId()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> presentedItems = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(
                anyList(), eq(PlayerId.of("plr1")), presentedItems.capture(), anyList());
        assertThat(presentedItems.getValue()).extracting(i -> i.getId().asString())
                .containsExactly("itm00000000", "itm11111111");
    }

    @Test
    void leavesTheContainerEmptyWhenTheContainmentRollMisses() {
        givenSeed(seed(twoConnectedScenes(), "scn1",
                containedOnlyItem("itm1"),
                containerItem("itm4", List.of(new ContainsEntry("itm1", 1, 45)), 1, 1, 1, "scn1")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // The chest spawns (roll, scene pick, 8 glyphs); its containment roll misses, so no id is minted for
        // the dagger — the pick script would throw on an unscripted ninth-plus pull.
        dice.willRoll(true, false).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        assertThat(saved.getValue().isContainer()).isTrue();
    }

    @Test
    void rejectsContainsDeclaredOnANonContainerAndDoesNotInitialize() {
        // itm1 declares contents without the container capability — an authoring shape violation at the gate.
        givenSeed(seed(twoConnectedScenes(), "scn1",
                new ItemEntry("itm1", "A rusty dagger.", "A plain iron dagger.", false, null,
                        List.of(new ContainsEntry("itm2", 1, 2)), null, null, false)));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAContainmentTargetThatDoesNotResolveAndDoesNotInitialize() {
        // itm9 resolves to no authored item — the inter-template twin of an unknown spawn scene.
        givenSeed(seed(twoConnectedScenes(), "scn1",
                containerItem("itm4", List.of(new ContainsEntry("itm9", 1, 45)), 1, 1, 1, "scn1")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentItemContainmentTargetInvalid(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("itm4");
        assertThat(captor.getValue().get("itm4")).containsExactly("itm9");
        verifyNothingInitialized();
    }

    @Test
    void rejectsAContainmentTargetThatIsItselfAContainerAndDoesNotInitialize() {
        // Nesting is not authored in this slice: a chest may not declare another chest among its contents.
        // This is also the guard against the template cycle below (each chest containing the other), which
        // would otherwise mint instances without bound at fill time.
        givenSeed(seed(twoConnectedScenes(), "scn1",
                containerItem("itm4", List.of(new ContainsEntry("itm5", 1, 2)), 1, 1, 1, "scn1"),
                containerItem("itm5", List.of(new ContainsEntry("itm4", 1, 2)), 1, 1, 1, "scn1")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentItemContainmentTargetInvalid(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("itm4", "itm5");
        assertThat(captor.getValue().get("itm4")).containsExactly("itm5");
        assertThat(captor.getValue().get("itm5")).containsExactly("itm4");
        verifyNothingInitialized();
    }

    // --- locked exits and the items that open them ----------------------------------------------

    @Test
    void spawnsAKeyOpeningALockDeclaredOnAnAuthoredExit() {
        givenSeed(seed(scenesWithLockedStair("guest-room", "The door is locked."), "scn1",
                key("itm6", "guest-room")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Scene> savedScenes = ArgumentCaptor.forClass(Scene.class);
        verify(sceneOps, times(2)).saveScene(savedScenes.capture());
        assertThat(savedScenes.getAllValues().get(0).exitNamed("up").orElseThrow().getLock())
                .isEqualTo(new Lock("guest-room", "The door is locked."));
        ArgumentCaptor<Item> savedItem = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(savedItem.capture());
        assertThat(savedItem.getValue().getOpens()).isEqualTo("guest-room");
    }

    @Test
    void rejectsAnItemOpeningAnUndeclaredLockAndDoesNotInitialize() {
        givenSeed(seed(scenesWithLockedStair("guest-room", null), "scn1",
                key("itm6", "guest-room"), key("itm7", "cellar")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentItemOpensUnknownLock(captor.capture());
        assertThat(captor.getValue()).containsExactly(Map.entry("itm7", "cellar"));
        verifyNothingInitialized();
    }

    @Test
    void rejectsALockedDescriptionWithoutALockAndDoesNotInitialize() {
        List<SceneEntry> entries = List.of(
                new SceneEntry("scn1", "Old Gate", "A weathered archway.", "The gate's hinges have rusted shut.",
                        List.of(new ExitEntry("east", "scn1", null, "The door is locked.")), List.of()));
        givenSeed(seed(entries, "scn1"));

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void acceptsALockThatNoItemOpens() {
        // A sealed door: valid authoring, the world initializes.
        givenSeed(seed(scenesWithLockedStair("guest-room", null), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(presenter, never()).presentItemOpensUnknownLock(any());
        verify(presenter).presentGameInitialized(anyList(), eq(PlayerId.of("plr1")), anyList(), anyList());
    }

    // --- portability resolution at the gate -----------------------------------------------------

    @Test
    void plainItemsSpawnPortableWhileContainersDefaultToAnchored() {
        givenSeed(seed(twoConnectedScenes(), "scn1",
                item("itm1", 1, 1, 1, "scn1"),
                containerItem("itm4", List.of(), 1, 1, 1, "scn1")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // Each template consumes a spawn roll, a scene pick, and 8 id glyphs (dagger all-zero, chest all-one).
        dice.willRoll(true, true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps, times(2)).saveItem(saved.capture());
        // Neither entry authors `portable`, so the kind-sensitive defaults resolve at the gate:
        assertThat(saved.getAllValues().get(0).isAnchored()).isFalse();   // a plain item is portable
        assertThat(saved.getAllValues().get(1).isAnchored()).isTrue();    // a container is anchored
    }

    @Test
    void anAuthoredPortableTrueMakesAContainerCarryable() {
        givenSeed(seed(twoConnectedScenes(), "scn1",
                new ItemEntry("itm6", "A barnacled sea chest.", "A sea chest crusted white with barnacle.",
                        true, Boolean.TRUE, List.of(), new SpawnEntry(List.of("scn1"), 1, 1, 1), null, false)));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        // The authored fact overrides the container default — the transportable chest is explicit.
        assertThat(saved.getValue().isContainer()).isTrue();
        assertThat(saved.getValue().isAnchored()).isFalse();
    }

    @Test
    void anAuthoredPortableFalseAnchorsAPlainItem() {
        givenSeed(seed(twoConnectedScenes(), "scn1",
                new ItemEntry("itm8", "A granite anvil.", "An anvil nobody carries anywhere.",
                        false, Boolean.FALSE, null, new SpawnEntry(List.of("scn1"), 1, 1, 1), null, false)));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        // The authored fact anchors a non-container too — portability is a general item fact.
        assertThat(saved.getValue().isAnchored()).isTrue();
    }

    // --- npc spawning ---------------------------------------------------------------------------

    @Test
    void spawnsNpcsByTheRollsAndPresentsThem() {
        givenSeed(seedWithNpcs(twoConnectedScenes(), "scn1", npc("npc1", 1, 4, 1, 1, 1, "scn1", "scn2")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // One NPC over two candidate scenes, always-hit spawn chance, one try: roll hits, pick scene index 1
        // (scn2), then mint the id by picking 8 alphabet glyphs — all index 0 ('0') -> "npc00000000".
        dice.willRoll(true).willPick(1, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(NpcId.of("npc00000000"));
        assertThat(saved.getValue().getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        assertThat(saved.getValue().getMoveChance().getNumerator()).isEqualTo(1);
        assertThat(saved.getValue().getMoveChance().getDenominator()).isEqualTo(4);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Npc>> presentedNpcs = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(anyList(), eq(PlayerId.of("plr1")), anyList(), presentedNpcs.capture());
        assertThat(presentedNpcs.getValue()).extracting(n -> n.getId().asString()).containsExactly("npc00000000");
    }

    @Test
    void doesNotReSpawnNpcsWhenNpcsAlreadyExist() {
        givenSeed(seedWithNpcs(twoConnectedScenes(), "scn1", npc("npc1", 1, 4, 1, 1, 1, "scn1")));
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1")))
                .thenReturn(Optional.of(player("plr1", "scn1")));
        when(npcOps.npcsAlreadySpawned()).thenReturn(true);
        // The rolls still happen (outside the transaction) — scene pick + 8 id glyphs — but the guard means
        // nothing is saved.
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(npcOps, never()).saveNpc(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Npc>> presentedNpcs = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(anyList(), eq(PlayerId.of("plr1")), anyList(), presentedNpcs.capture());
        assertThat(presentedNpcs.getValue()).isEmpty();
    }

    @Test
    void rejectsAnNpcWithAnInvalidMoveChanceAndDoesNotInitialize() {
        // Move-chance denominator 0 — Chance construction fails the intra-aggregate validity gate.
        givenSeed(seedWithNpcs(twoConnectedScenes(), "scn1", npc("npc1", 1, 0, 1, 1, 1, "scn1")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnNpcSpawningIntoAnUnknownSceneAndDoesNotInitialize() {
        // scn9 is a well-formed id but no authored scene defines it — an inter-aggregate failure.
        givenSeed(seedWithNpcs(twoConnectedScenes(), "scn1", npc("npc1", 1, 4, 1, 2, 1, "scn9")));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<SceneId>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentNpcSpawnSceneUnknown(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("npc1");
        assertThat(captor.getValue().get("npc1")).containsExactly(SceneId.of("scn9"));
        verifyNothingInitialized();
    }

    // --- corpse refs resolve to valid corpse templates (issue #93) -------------------------------

    @Test
    void spawnsAnNpcCarryingItsValidCorpseRef() {
        // itm5 is a proper corpse template: a container with no ground-spawn rule.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(corpseItem("itm5")), List.of(npcWithCorpse("npc1", "itm5", "scn1"))));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // The corpse template never rolls (no spawn rule); the NPC rolls: hit, scene pick, 8 id glyphs.
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        assertThat(saved.getValue().getCorpseRef()).isEqualTo("itm5");
        // The corpse template itself spawned nothing at init — the death drop is its only placement route.
        verify(itemOps, never()).saveItem(any());
    }

    @Test
    void rejectsACorpseRefThatDoesNotResolveAndDoesNotInitialize() {
        // No authored item is named itm9 — the ref dangles.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(corpseItem("itm5")), List.of(npcWithCorpse("npc1", "itm9", "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        assertCorpseRefRejected("npc1", "itm9");
    }

    @Test
    void rejectsACorpseRefToANonContainerAndDoesNotInitialize() {
        // A corpse must be able to hold its loot — a plain (non-container) template does not qualify.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(containedOnlyItem("itm9")), List.of(npcWithCorpse("npc1", "itm9", "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        assertCorpseRefRejected("npc1", "itm9");
    }

    @Test
    void rejectsACorpseRefToAGroundSpawningContainerAndDoesNotInitialize() {
        // A container with its own spawn rule would ALSO lie around at init — the death drop must be the
        // corpse's only placement route.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(containerItem("itm4", List.of(), 1, 1, 1, "scn1")),
                List.of(npcWithCorpse("npc1", "itm4", "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        // The item-spawn rolls (checkpoint 9) run before the corpse-ref checkpoint — script the chest's
        // single try to miss, so no id glyphs are pulled and nothing spawns.
        dice.willRoll(false);

        useCase.systemInitializesGame();

        assertCorpseRefRejected("npc1", "itm4");
    }

    private void assertCorpseRefRejected(String expectedNpcId, String expectedRef) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentNpcCorpseRefInvalid(captor.capture());
        assertThat(captor.getValue()).containsExactly(entry(expectedNpcId, expectedRef));
        verifyNothingInitialized();
    }

    // --- offer items resolve to sellable authored items (issue #117) ------------------------------

    @Test
    void spawnsAnNpcCarryingItsOffersInAuthoredOrder() {
        // The keeper of the inn sells the guest-room key (which also lies on the inn floor: an offered item MAY
        // carry a ground-spawn rule — the placement routes are independent) and a contained-only dagger.
        givenSeed(seedWithItemsAndNpcs(scenesWithLockedStair("guest-room", null), "scn1",
                List.of(key("itm6", "guest-room"), containedOnlyItem("itm2")),
                List.of(npcWithOffers("npc2", List.of(
                        offer("room", "A room for the night.", 2, "itm6"),
                        offer("blade", "A rusty dagger for the road.", 5, "itm2")), "scn1"))));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        // The key rolls first (the item spawn, outside the transaction): hit, scene pick, 8 id glyphs. The
        // contained-only knife never rolls. Then the NPC: hit, scene pick, 8 id glyphs.
        dice.willRoll(true, true)
                .willPick(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        // Copied from the template onto the instance, in authored order — the sayings rule applied to wares.
        assertThat(saved.getValue().getOffers()).containsExactly(
                new Offer("room", "A room for the night.", 2, "itm6"),
                new Offer("blade", "A rusty dagger for the road.", 5, "itm2"));
        verify(presenter, never()).presentNpcOfferItemInvalid(any());
    }

    @Test
    void acceptsAnNpcWithNoOffers() {
        // Authored absence: an NPC that sells nothing is an ordinary NPC (the silent-NPC twin).
        givenSeed(seedWithNpcs(twoConnectedScenes(), "scn1", npc("npc1", 1, 4, 1, 1, 1, "scn1")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Npc> saved = ArgumentCaptor.forClass(Npc.class);
        verify(npcOps).saveNpc(saved.capture());
        assertThat(saved.getValue().getOffers()).isEmpty();
        verify(presenter, never()).presentNpcOfferItemInvalid(any());
    }

    @Test
    void rejectsAnOfferForAnUnknownItemAndDoesNotInitialize() {
        // No authored item is named itm9 — the delivered ware dangles, the offer twin of an unknown spawn scene.
        // No item is authored at all and nothing is scripted on the dice, so a spawn roll for the NPC — which
        // the checkpoint must preempt — would pull from an empty script and fail the test.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(), List.of(npcWithOffers("npc2", List.of(offer("room", "A room.", 2, "itm9")), "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        assertOfferItemRejected("npc2", "itm9");
    }

    @Test
    void rejectsAnOfferForAContainerAndDoesNotInitialize() {
        // Ware is handed over as one thing, not as a bundle whose contents ride along uncounted. The chest is
        // authored `portable: true`, so portability is satisfied and the container capability alone condemns it.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(new ItemEntry("itm4", "An oak chest.", "A heavy oak chest.", true, Boolean.TRUE,
                        List.of(), null, null, false)),
                List.of(npcWithOffers("npc2", List.of(offer("chest", "A sea-worthy chest.", 4, "itm4")), "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        assertOfferItemRejected("npc2", "itm4");
    }

    @Test
    void rejectsAnOfferForAnAnchoredItemAndDoesNotInitialize() {
        // Ware fixed to the ground cannot be paid for and carried off. The anvil's own ground-spawn roll (before
        // this checkpoint) is scripted to miss, and nothing more is scripted — so an NPC spawn roll would fail.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(anchoredItem("itm8")),
                List.of(npcWithOffers("npc2", List.of(offer("anvil", "A granite anvil.", 9, "itm8")), "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        dice.willRoll(false);

        useCase.systemInitializesGame();

        assertOfferItemRejected("npc2", "itm8");
    }

    @Test
    void rejectsEveryOffendingOfferOfAnNpcInOnePresentation() {
        // One NPC, two offers, both bad (one dangling, one a container): the stripe carries both refs — the
        // containment-target shape, not the corpse check's one-ref-per-NPC map.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(new ItemEntry("itm4", "An oak chest.", "A heavy oak chest.", true, Boolean.TRUE,
                        List.of(), null, null, false)),
                List.of(npcWithOffers("npc2", List.of(
                        offer("room", "A room.", 2, "itm9"),
                        offer("chest", "A sea-worthy chest.", 4, "itm4")), "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        assertOfferItemRejected("npc2", "itm9", "itm4");
    }

    @Test
    void rejectsADuplicateOfferHandleAndDoesNotInitialize() {
        // A handle a later phase's player would name can mean only one thing within one NPC: an entry-shape
        // violation of the authored NPC, so it is invalid parameters, not a presented inter-template outcome.
        givenSeed(seedWithItemsAndNpcs(scenesWithLockedStair("guest-room", null), "scn1",
                List.of(key("itm6", "guest-room")),
                List.of(npcWithOffers("npc2", List.of(
                        offer("room", "A room for the night.", 2, "itm6"),
                        offer("room", "A loft over the stable.", 3, "itm6")), "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        dice.willRoll(false);   // the key's own ground spawn, scripted to miss, before the NPC gate

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnOfferPricedBelowOneCoinAndDoesNotInitialize() {
        // A free offer sells nothing — the Offer gate rejects it at the NPC template checkpoint.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(), List.of(npcWithOffers("npc2", List.of(offer("room", "A room.", 0, "itm6")), "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsAnOfferWithoutAnItemAndDoesNotInitialize() {
        // The missing `item:` of the seed arrives as a null handle on the carrier and is refused by the gate.
        givenSeed(seedWithItemsAndNpcs(twoConnectedScenes(), "scn1",
                List.of(), List.of(npcWithOffers("npc2", List.of(offer("room", "A room.", 2, null)), "scn1"))));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void spawnsACurrencyItemCarryingTheFlagThoughNoOfferPricesIt() {
        // Valid on purpose: money nobody spends. Nothing counts a coin in this phase — the flag is authored,
        // gated and copied onto the instance, and that is all.
        givenSeed(seed(twoConnectedScenes(), "scn1", coin("itm3")));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        assertThat(saved.getValue().isCurrency()).isTrue();
        assertThat(saved.getValue().isAnchored()).isFalse();   // unauthored portable, a plain item: carryable
    }

    @Test
    void rejectsACurrencyItemThatIsAContainerAndDoesNotInitialize() {
        // A purse is not a coin: currency holds nothing (and, by the container default, is anchored besides).
        givenSeed(seed(twoConnectedScenes(), "scn1",
                new ItemEntry("itm7", "A coin purse.", "A purse heavy with coins.",
                        true, null, List.of(), null, null, true)));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    @Test
    void rejectsACurrencyItemAuthoredNotPortableAndDoesNotInitialize() {
        // `currency: true` together with `portable: false` is invalid parameters: money bolted down cannot be paid.
        givenSeed(seed(twoConnectedScenes(), "scn1",
                new ItemEntry("itm7", "A heavy millstone coin.", "A coin as big as a millstone.",
                        false, Boolean.FALSE, null, null, null, true)));
        when(playerOps.currentPlayerId()).thenReturn("plr1");

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    private void assertOfferItemRejected(String expectedNpcId, String... expectedItemRefs) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentNpcOfferItemInvalid(captor.capture());
        assertThat(captor.getValue()).containsExactly(entry(expectedNpcId, List.of(expectedItemRefs)));
        verifyNothingInitialized();
    }

    // --- authored mini-games pass the gate onto the scene aggregate -----------------------------

    @Test
    void constructsScenesCarryingTheirAuthoredMiniGames() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));   // scn2 authors "blackjack", scn1 authors none
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        ArgumentCaptor<Scene> captor = ArgumentCaptor.forClass(Scene.class);
        verify(sceneOps, times(2)).saveScene(captor.capture());
        assertThat(captor.getAllValues().get(0).offers(MiniGame.BLACKJACK)).isFalse();
        assertThat(captor.getAllValues().get(1).offers(MiniGame.BLACKJACK)).isTrue();
    }

    @Test
    void rejectsAnUnknownMiniGameNameAndDoesNotInitialize() {
        // 'poker' is not in the closed MiniGame vocabulary — invalid authored input at the construction gate.
        List<SceneEntry> entries = List.of(
                new SceneEntry("scn1", "Old Gate", "A gate.", "A weathered stone archway.",
                        List.of(), List.of("poker")));
        givenSeed(seed(entries, "scn1"));

        useCase.systemInitializesGame();

        verify(presenter).presentInvalidParametersError(any(InvalidDomainObjectError.class));
        verifyNothingInitialized();
    }

    // --- a world that fails to construct stops the interaction before any player ----------------

    @Test
    void rejectsAMalformedSceneEntryAndDoesNotInitialize() {
        // id without the 'scn' prefix — SceneId construction fails the intra-aggregate validity gate
        List<SceneEntry> entries = List.of(
                new SceneEntry("bogus", "Old Gate", "A gate.", "A weathered stone archway.", List.of(), List.of()));
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
                        List.of(new ExitEntry("east", "scn9", null, null)), List.of()));
        givenSeed(seed(entries, "scn1"));

        useCase.systemInitializesGame();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<SceneId, List<Exit>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(presenter).presentErrorWhenExitTargetUnknown(captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys(SceneId.of("scn1"));
        assertThat(captor.getValue().get(SceneId.of("scn1")))
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

        verify(presenter).presentStartingSceneUnknown(SceneId.of("scn9"));
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
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any(), any(), any());
    }

    @Test
    void presentsAnErrorWhenThePlayerCannotBeSaved() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));
        when(sceneOps.worldIsEmpty()).thenReturn(false);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(playerRepositoryOps).savePlayer(any());
        runTransactionWithResult(txOps);

        useCase.systemInitializesGame();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameInitialized(any(), any(), any(), any());
    }

    // --- fixtures -------------------------------------------------------------------------------

    /** Stub the source port to yield the given authored seed — the use case pulls it as Checkpoint 1. */
    private void givenSeed(GameSeed gameSeed) {
        when(seedSourceOps.loadGameSeed()).thenReturn(gameSeed);
    }

    private static GameSeed seed(List<SceneEntry> scenes, String startingSceneId, ItemEntry... items) {
        return new GameSeed(scenes, startingSceneId, 30, List.of(items), List.of());
    }

    private static GameSeed seedWithNpcs(List<SceneEntry> scenes, String startingSceneId, NpcEntry... npcs) {
        return new GameSeed(scenes, startingSceneId, 30, List.of(), List.of(npcs));
    }

    /** A seed carrying both authored items and NPCs — what the corpse-ref checkpoint resolves across. */
    private static GameSeed seedWithItemsAndNpcs(List<SceneEntry> scenes, String startingSceneId,
                                                 List<ItemEntry> items, List<NpcEntry> npcs) {
        return new GameSeed(scenes, startingSceneId, 30, items, npcs);
    }

    /** A corpse template: a container with no ground-spawn rule — placed only by the death drop. */
    private static ItemEntry corpseItem(String id) {
        return new ItemEntry(id, "The corpse of a hooded wanderer.", "The wanderer lies where it fell.",
                true, null, null, null, null, false);
    }

    private static NpcEntry npc(String id, int moveNumerator, int moveDenominator,
                                int chanceNumerator, int chanceDenominator, int max, String... candidateScenes) {
        return new NpcEntry(id, "A hooded wanderer.", "A cloaked figure.",
                new SpawnEntry(List.of(candidateScenes), chanceNumerator, chanceDenominator, max),
                moveNumerator, moveDenominator, 1, 3, 10, null, List.of(), List.of());
    }

    /** An always-spawning NPC (one try, certain) declaring the given corpse ref. */
    private static NpcEntry npcWithCorpse(String id, String corpseRef, String... candidateScenes) {
        return new NpcEntry(id, "A hooded wanderer.", "A cloaked figure.",
                new SpawnEntry(List.of(candidateScenes), 1, 1, 1), 1, 4, 1, 3, 10, corpseRef, List.of(), List.of());
    }

    /** An always-spawning NPC (one try, certain) declaring the given offers — the keeper of the authored inn. */
    private static NpcEntry npcWithOffers(String id, List<OfferEntry> offers, String... candidateScenes) {
        return new NpcEntry(id, "A stout innkeeper.", "A broad-shouldered woman in a flour-dusted apron.",
                new SpawnEntry(List.of(candidateScenes), 1, 1, 1), 0, 1, 1, 4, 25, null, List.of(), offers);
    }

    /** One authored offer: the handle, the line the player reads, the price in coins, the delivered item. */
    private static OfferEntry offer(String id, String name, int price, String item) {
        return new OfferEntry(id, name, price, item);
    }

    private static List<SceneEntry> twoConnectedScenes() {
        return List.of(
                new SceneEntry("scn1", "Old Gate", "A weathered archway.",
                        "The gate's iron hinges have long since rusted shut.",
                        List.of(new ExitEntry("east", "scn2", null, null)), List.of()),
                new SceneEntry("scn2", "Courtyard", "A grass-cracked courtyard.",
                        "Weeds push between the flagstones of a drilling yard.",
                        List.of(new ExitEntry("west", "scn1", null, null)), List.of("blackjack")));
    }

    /** scn1 with an "up" exit to scn2 barred by the given lock; scn2's way "down" is open. */
    private static List<SceneEntry> scenesWithLockedStair(String lockRef, String lockedDescription) {
        return List.of(
                new SceneEntry("scn1", "The Crooked Lantern", "The common room of an inn.",
                        "A fire crackles in a soot-black hearth.",
                        List.of(new ExitEntry("up", "scn2", lockRef, lockedDescription)), List.of()),
                new SceneEntry("scn2", "Guest Room", "A guest room under the eaves.",
                        "A narrow bed and a washstand.",
                        List.of(new ExitEntry("down", "scn1", null, null)), List.of()));
    }

    /** A certain ground-spawning item in scn1 that opens the given lock. */
    private static ItemEntry key(String id, String opens) {
        return new ItemEntry(id, "A small brass key.", "A small brass key on a loop of cord.", false, null, null,
                new SpawnEntry(List.of("scn1"), 1, 1, 1), opens, false);
    }

    private static ItemEntry item(String id, int chanceNumerator, int chanceDenominator, int max,
                                  String... candidateScenes) {
        return new ItemEntry(id, "A rusty dagger.", "A plain iron dagger, rusty but usable.", false, null, null,
                new SpawnEntry(List.of(candidateScenes), chanceNumerator, chanceDenominator, max), null, false);
    }

    /** A contained-only item: no spawn rule (never on the ground), appears only through containers. */
    private static ItemEntry containedOnlyItem(String id) {
        return new ItemEntry(id, "A rusty dagger.", "A plain iron dagger, rusty but usable.",
                false, null, null, null, null, false);
    }

    /** A container with unauthored {@code portable} — the gate's container default (anchored) applies. */
    private static ItemEntry containerItem(String id, List<ContainsEntry> contains, int chanceNumerator,
                                           int chanceDenominator, int max, String... candidateScenes) {
        return new ItemEntry(id, "An oak chest.", "A heavy oak chest banded in black iron.", true, null, contains,
                new SpawnEntry(List.of(candidateScenes), chanceNumerator, chanceDenominator, max), null, false);
    }

    /** A plain item the author explicitly anchors — ware an NPC cannot hand over, so it cannot be offered. */
    private static ItemEntry anchoredItem(String id) {
        return new ItemEntry(id, "A granite anvil.", "An anvil nobody carries anywhere.",
                false, Boolean.FALSE, null, new SpawnEntry(List.of("scn1"), 1, 1, 1), null, false);
    }

    /** A currency item: the authored money flag, unauthored portable (a plain item, so carryable). */
    private static ItemEntry coin(String id) {
        return new ItemEntry(id, "A copper coin.", "A single dull copper coin.",
                false, null, null, new SpawnEntry(List.of("scn1"), 1, 1, 1), null, true);
    }

    private static Player player(String id, String currentScene) {
        return Player.builder().id(PlayerId.of(id)).currentScene(SceneId.of(currentScene))
                .hitPoints(HitPoints.full(30)).version(1).build();
    }

    // --- assertion helpers ----------------------------------------------------------------------

    private void assertScenesSavedInOrder(String... expectedIds) {
        ArgumentCaptor<Scene> captor = ArgumentCaptor.forClass(Scene.class);
        verify(sceneOps, times(expectedIds.length)).saveScene(captor.capture());
        assertThat(captor.getAllValues()).extracting(scene -> scene.getId().asString())
                .containsExactly(expectedIds);
    }

    private void assertPlayerSaved(String expectedId, String expectedScene) {
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepositoryOps).savePlayer(captor.capture());
        assertThat(captor.getValue().getId().asString()).isEqualTo(expectedId);
        assertThat(captor.getValue().getCurrentScene().asString()).isEqualTo(expectedScene);
    }

    private void assertGameInitializedPresentedWithNoItems(String expectedPlayerId, String... expectedSceneIds) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Scene>> scenesCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Item>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Npc>> npcsCaptor = ArgumentCaptor.forClass(List.class);
        verify(presenter).presentGameInitialized(scenesCaptor.capture(), eq(PlayerId.of(expectedPlayerId)),
                itemsCaptor.capture(), npcsCaptor.capture());
        assertThat(scenesCaptor.getValue()).extracting(scene -> scene.getId().asString())
                .containsExactly(expectedSceneIds);
        assertThat(itemsCaptor.getValue()).isEmpty();
        assertThat(npcsCaptor.getValue()).isEmpty();
    }

    private void verifyNothingInitialized() {
        verifyNoInteractions(txOps);
        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(itemOps, never()).saveItem(any());
        verify(npcOps, never()).saveNpc(any());
        verify(gameClockRepositoryOps, never()).saveClock(any());
        verify(dayPhaseLogRepositoryOps, never()).saveDayPhaseLog(any());
        verify(presenter, never()).presentGameInitialized(any(), any(), any(), any());
    }
}
