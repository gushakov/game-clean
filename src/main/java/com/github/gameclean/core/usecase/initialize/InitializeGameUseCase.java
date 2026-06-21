package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.model.daytime.DayPhaseLog;
import com.github.gameclean.core.model.item.Chance;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.ItemTemplate;
import com.github.gameclean.core.model.item.SpawnRule;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.id.IdGeneratorOperationsOutputPort;
import com.github.gameclean.core.port.persistence.DayPhaseLogRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.randomness.RandomnessOperationsOutputPort;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.core.port.seed.SceneEntry;
import com.github.gameclean.core.port.seed.SpawnEntry;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Brings a fresh game into a playable starting state: pulls the authored seed, constructs the authored
 * world, places the single player, and spawns the authored items. Implementation of
 * {@link InitializeGameInputPort}; framework-free, wired by the composition root, exercised in isolation
 * against mocked ports.
 *
 * <p><b>The use case pulls its own input.</b> The seed is not pushed in by a driving adapter; the use case
 * fetches it as Checkpoint 1 through {@link GameSeedSourceOperationsOutputPort} (an adapter behind that
 * port owns the YAML parsing). So loading the world is the interaction's own first step — the application
 * logic is driven from here, not from a seeder. A source failure has no special branch: it propagates to
 * the single outermost {@code catch} and is presented, uniform with a persistence fault.
 *
 * <p><b>One interaction, one runtime presentation.</b> Constructing the world, placing the player and
 * spawning items are three <em>phases</em> of a single system-actor goal, not three interactions: a player
 * needs a scene to stand in and items need scenes to spawn into, so the world→player and world→items orders
 * are <em>domain</em> preconditions enforced <em>inside</em> this one interaction rather than sequenced by a
 * caller across the hexagon boundary. The decisive point is that the phases do <em>not</em> each present. A
 * {@code present*} call relinquishes control for good (unidirectional flow), so on any execution path exactly
 * <em>one</em> {@code present*} is reached and it is the last act of the interaction — never "present, then
 * continue." The world and item phases are therefore non-presenting checkpoints feeding the single success
 * outcome, {@link InitializeGamePresenterOutputPort#presentGameInitialized}. "World already seeded", "player
 * already present" and "items already spawned" are folded into that one success: the system actor's goal — a
 * playable game — is met identically whether the state was freshly written or already there.
 *
 * <p>The checkpoints run transaction-tight. Construction, resolution and the random spawn rolls all run
 * <em>outside</em> any transaction: the intra-aggregate validity gate (value-object construction), then the
 * inter-aggregate rules that every exit target, the starting scene, and every item's candidate spawn scenes
 * resolve to an authored scene — resolved against the in-memory world being initialized, since on a first run
 * the store is not seeded yet. <b>Item spawning is non-deterministic</b> but its rolls have no persistence
 * side effect, so they too run outside the transaction; a single {@code doInTransaction} then holds the three
 * idempotency guards (seed-if-empty, create-if-absent player/clock/day-phase-log, spawn-if-none) together with their writes, so those
 * read-then-write decisions cannot interleave with a concurrent initialization, and a restart re-rolls
 * harmlessly but never re-persists. The lone success presentation is deferred to after-commit so it is never
 * reported before the data is durable, and the interaction returns immediately after registering it. The
 * initiating actor is the system at startup, so there is no security assertion. The single outermost
 * {@code catch} routes any unhandled error (notably a {@code PersistenceOperationsError}, which has already
 * rolled its transaction back) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InitializeGameUseCase implements InitializeGameInputPort {

    InitializeGamePresenterOutputPort presenter;
    GameSeedSourceOperationsOutputPort seedSourceOps;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    ItemRepositoryOperationsOutputPort itemOps;
    GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    DayPhaseLogRepositoryOperationsOutputPort dayPhaseLogRepositoryOps;
    IdGeneratorOperationsOutputPort idGeneratorOps;
    RandomnessOperationsOutputPort randomnessOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void systemInitializesGame() {
        try {
            // Initiating actor: the system at startup — no security assertion is required.

            // Checkpoint 1 — pull the authored seed. Loading the world is the use case's own first step,
            // not logic stranded in a driving adapter; a source failure (missing/broken seed) propagates
            // to the outermost catch and is presented, uniform with a persistence fault.
            GameSeed seed = seedSourceOps.loadGameSeed();

            // Checkpoint 2 — construct the scene aggregates (intra-aggregate validity gate).
            List<Scene> scenes;
            try {
                scenes = buildScenes(seed.getScenes());
            } catch (InvalidDomainObjectError e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 3 — inter-aggregate rule: every exit target resolves to an authored scene.
            Map<SceneId, List<Exit>> unresolvedExitsByScene = findUnresolvedExits(scenes);
            if (!unresolvedExitsByScene.isEmpty()) {
                presenter.presentErrorWhenExitTargetUnknown(unresolvedExitsByScene);
                return;
            }

            // Checkpoint 4 — construct the player value objects and aggregate (validity gate). The acting
            // player's id is ambient (pulled from playerOps); the starting scene is the authored carrier.
            PlayerId playerId;
            SceneId startingScene;
            Player player;
            try {
                playerId = new PlayerId(playerOps.currentPlayerId());
                startingScene = new SceneId(seed.getStartingSceneId());
                player = Player.builder().id(playerId).currentScene(startingScene).build();
            } catch (InvalidDomainObjectError e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 5 — inter-aggregate rule: the starting scene resolves to an authored scene.
            // Resolved against the in-memory world (like the exit check), not the store: on a first run
            // the scenes are not persisted yet, so a store lookup here would be a false negative.
            if (scenes.stream().noneMatch(scene -> scene.getId().equals(startingScene))) {
                presenter.presentStartingSceneUnknown(startingScene);
                return;
            }

            // Checkpoint 6 — construct the item templates from the authored items (validity gate). Each
            // template validates its descriptions and its spawn rule (valid chance, non-negative tries, at
            // least one candidate scene) up front, independent of how the spawn later rolls.
            List<AuthoredItem> authoredItems;
            try {
                authoredItems = buildAuthoredItems(seed.getItems());
            } catch (InvalidDomainObjectError e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 7 — inter-aggregate rule: every item's candidate spawn scenes resolve to an
            // authored scene. Resolved in-memory against the world being built, like the exit check.
            Map<String, List<SceneId>> unknownSpawnScenes = findUnknownSpawnScenes(authoredItems, scenes);
            if (!unknownSpawnScenes.isEmpty()) {
                presenter.presentItemSpawnSceneUnknown(unknownSpawnScenes);
                return;
            }

            // Checkpoint 8 — roll and place the item instances. Non-deterministic, but a pure in-memory
            // construction with no persistence side effect, so it runs outside the transaction.
            List<Item> spawnedItems = spawnItems(authoredItems);

            // Checkpoint 9 — one outcome, one atomic unit. A single transaction seeds the world if it is
            // still empty, creates the player if none exists yet, spawns items if none were spawned yet,
            // creates the world clock at time zero if none exists yet, and seeds the day-phase log at its
            // sentinel if none exists yet; holding all five read-then-write guards in one transaction stops a
            // concurrent initialization from double-seeding, double-creating, or double-spawning. Exactly one after-commit presentation reports the
            // single success — carrying the items spawned this run (empty if already spawned) — and the
            // interaction ends here, since nothing runs past a presentation.
            txOps.doInTransaction(false, () -> {
                if (sceneOps.worldIsEmpty()) {
                    scenes.forEach(sceneOps::saveScene);
                }
                if (playerRepositoryOps.findPlayer(playerId).isEmpty()) {
                    playerRepositoryOps.savePlayer(player);
                }
                List<Item> reportedItems;
                if (itemOps.itemsAlreadySpawned()) {
                    reportedItems = List.of();
                } else {
                    spawnedItems.forEach(itemOps::saveItem);
                    reportedItems = spawnedItems;
                }
                // The clock has no domain precondition on the world/player/items — it is independent
                // world-singleton state — so it is just another create-if-absent guard, not an ordered phase.
                if (gameClockRepositoryOps.findClock().isEmpty()) {
                    gameClockRepositoryOps.saveClock(GameClock.initial());
                }
                // Likewise the day-phase log: independent world-singleton state (the watermark the time
                // ticker dedups dawn/dusk announcements against), seeded at the "nothing announced" sentinel.
                if (dayPhaseLogRepositoryOps.findDayPhaseLog().isEmpty()) {
                    dayPhaseLogRepositoryOps.saveDayPhaseLog(DayPhaseLog.initial());
                }
                txOps.doAfterCommit(() -> presenter.presentGameInitialized(scenes, playerId, reportedItems));
            });
            return;

        } catch (Exception e) {
            // Outermost checkpoint. Anything not handled above ends here — notably a
            // PersistenceOperationsError from a write, which has also rolled its transaction back before
            // propagating out of doInTransaction.
            presenter.presentError(e);
        }
    }

    private static List<Scene> buildScenes(List<SceneEntry> entries) {
        List<Scene> scenes = new ArrayList<>(entries.size());
        for (SceneEntry entry : entries) {
            scenes.add(Scene.builder()
                    .id(new SceneId(entry.getId()))
                    .name(entry.getName())
                    .shortDescription(entry.getShortDescription())
                    .fullDescription(entry.getFullDescription())
                    .exits(entry.getExits().stream()
                            .map(exit -> new Exit(exit.getName(), new SceneId(exit.getTarget())))
                            .toList())
                    .build());
        }
        return scenes;
    }

    private static Map<SceneId, List<Exit>> findUnresolvedExits(List<Scene> scenes) {
        Set<SceneId> known = scenes.stream().map(Scene::getId).collect(Collectors.toSet());
        Map<SceneId, List<Exit>> unresolved = new LinkedHashMap<>();
        for (Scene scene : scenes) {
            List<Exit> dangling = scene.exitsWithTargetNotIn(known);
            if (!dangling.isEmpty()) {
                unresolved.put(scene.getId(), dangling);
            }
        }
        return unresolved;
    }

    private static List<AuthoredItem> buildAuthoredItems(List<ItemEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        List<AuthoredItem> authored = new ArrayList<>(entries.size());
        for (ItemEntry entry : entries) {
            SpawnEntry spawn = entry.getSpawn();
            if (spawn == null) {
                throw new InvalidDomainObjectError(
                        "item '%s' has no spawn rule".formatted(entry.getId()));
            }
            Chance chance = new Chance(spawn.getChanceNumerator(), spawn.getChanceDenominator());
            List<SceneId> candidateScenes = spawn.getScenes().stream().map(SceneId::new).toList();
            SpawnRule rule = new SpawnRule(chance, spawn.getMax(), candidateScenes);
            ItemTemplate template = new ItemTemplate(entry.getShortDescription(), entry.getFullDescription(), rule);
            authored.add(new AuthoredItem(entry.getId(), template));
        }
        return authored;
    }

    private static Map<String, List<SceneId>> findUnknownSpawnScenes(List<AuthoredItem> authoredItems,
                                                                     List<Scene> scenes) {
        Set<SceneId> known = scenes.stream().map(Scene::getId).collect(Collectors.toSet());
        Map<String, List<SceneId>> unknown = new LinkedHashMap<>();
        for (AuthoredItem item : authoredItems) {
            List<SceneId> dangling = item.candidateScenesNotIn(known);
            if (!dangling.isEmpty()) {
                unknown.put(item.getAuthoredId(), dangling);
            }
        }
        return unknown;
    }

    private List<Item> spawnItems(List<AuthoredItem> authoredItems) {
        List<Item> spawned = new ArrayList<>();
        for (AuthoredItem item : authoredItems) {
            spawned.addAll(item.spawnInto(idGeneratorOps::generateItemId, randomnessOps::nextDouble));
        }
        return spawned;
    }

    /**
     * Use-case-private pairing of an item's authoring handle (used only for diagnostics — e.g. reporting an
     * unknown spawn scene) with its always-valid {@link ItemTemplate}. The handle is not a domain identity,
     * so it stays out of the model. It forwards {@link #candidateScenesNotIn} and {@link #spawnInto} to the
     * template one level, so the use case tells the holder rather than reaching through it into the template
     * and rule: the application keeps only the orchestration (looping authored items, adapting the randomness
     * and id-generator ports to suppliers, collecting), while the whole spawn policy stays on the model.
     */
    @Value
    private static class AuthoredItem {
        String authoredId;
        ItemTemplate template;

        List<SceneId> candidateScenesNotIn(Set<SceneId> knownSceneIds) {
            return template.candidateScenesNotIn(knownSceneIds);
        }

        List<Item> spawnInto(Supplier<ItemId> ids, DoubleSupplier draws) {
            return template.spawnInto(ids, draws);
        }
    }
}
