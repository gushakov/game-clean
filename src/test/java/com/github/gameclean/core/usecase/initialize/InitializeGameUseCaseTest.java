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
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
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
import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransaction;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransactionAndFireAfterCommit;
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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
                        List.of(new ContainsEntry("itm2", 1, 2)), null)));
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
        runTransactionAndFireAfterCommit(txOps);

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
                        true, Boolean.TRUE, List.of(), new SpawnEntry(List.of("scn1"), 1, 1, 1))));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionAndFireAfterCommit(txOps);

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
                        false, Boolean.FALSE, null, new SpawnEntry(List.of("scn1"), 1, 1, 1))));
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        dice.willRoll(true).willPick(0, 0, 0, 0, 0, 0, 0, 0, 0);
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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
        runTransactionAndFireAfterCommit(txOps);

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

    // --- authored mini-games pass the gate onto the scene aggregate -----------------------------

    @Test
    void constructsScenesCarryingTheirAuthoredMiniGames() {
        givenSeed(seed(twoConnectedScenes(), "scn1"));   // scn2 authors "blackjack", scn1 authors none
        when(sceneOps.worldIsEmpty()).thenReturn(true);
        when(playerOps.currentPlayerId()).thenReturn("plr1");
        when(playerRepositoryOps.findPlayer(PlayerId.of("plr1"))).thenReturn(Optional.empty());
        runTransactionAndFireAfterCommit(txOps);

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
                        List.of(new ExitEntry("east", "scn9")), List.of()));
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
        runTransaction(txOps);

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
        runTransaction(txOps);

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

    private static NpcEntry npc(String id, int moveNumerator, int moveDenominator,
                                int chanceNumerator, int chanceDenominator, int max, String... candidateScenes) {
        return new NpcEntry(id, "A hooded wanderer.", "A cloaked figure.",
                new SpawnEntry(List.of(candidateScenes), chanceNumerator, chanceDenominator, max),
                moveNumerator, moveDenominator, 1, 3, 10);
    }

    private static List<SceneEntry> twoConnectedScenes() {
        return List.of(
                new SceneEntry("scn1", "Old Gate", "A weathered archway.",
                        "The gate's iron hinges have long since rusted shut.",
                        List.of(new ExitEntry("east", "scn2")), List.of()),
                new SceneEntry("scn2", "Courtyard", "A grass-cracked courtyard.",
                        "Weeds push between the flagstones of a drilling yard.",
                        List.of(new ExitEntry("west", "scn1")), List.of("blackjack")));
    }

    private static ItemEntry item(String id, int chanceNumerator, int chanceDenominator, int max,
                                  String... candidateScenes) {
        return new ItemEntry(id, "A rusty dagger.", "A plain iron dagger, rusty but usable.", false, null, null,
                new SpawnEntry(List.of(candidateScenes), chanceNumerator, chanceDenominator, max));
    }

    /** A contained-only item: no spawn rule (never on the ground), appears only through containers. */
    private static ItemEntry containedOnlyItem(String id) {
        return new ItemEntry(id, "A rusty dagger.", "A plain iron dagger, rusty but usable.",
                false, null, null, null);
    }

    /** A container with unauthored {@code portable} — the gate's container default (anchored) applies. */
    private static ItemEntry containerItem(String id, List<ContainsEntry> contains, int chanceNumerator,
                                           int chanceDenominator, int max, String... candidateScenes) {
        return new ItemEntry(id, "An oak chest.", "A heavy oak chest banded in black iron.", true, null, contains,
                new SpawnEntry(List.of(candidateScenes), chanceNumerator, chanceDenominator, max));
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
        verify(txOps, never()).doInTransaction(anyBoolean(), any());
        verify(sceneOps, never()).saveScene(any());
        verify(playerRepositoryOps, never()).savePlayer(any());
        verify(itemOps, never()).saveItem(any());
        verify(npcOps, never()).saveNpc(any());
        verify(gameClockRepositoryOps, never()).saveClock(any());
        verify(dayPhaseLogRepositoryOps, never()).saveDayPhaseLog(any());
        verify(presenter, never()).presentGameInitialized(any(), any(), any(), any());
    }
}
