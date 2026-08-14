package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.daytime.DayPhaseLog;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.ItemTemplate;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcTemplate;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.MiniGame;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.model.spawn.SpawnRule;
import com.github.gameclean.core.port.persistence.DayPhaseLogRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.seed.ContainsEntry;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.core.port.seed.NpcEntry;
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
import java.util.Optional;
import java.util.Set;
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
    NpcRepositoryOperationsOutputPort npcOps;
    GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    DayPhaseLogRepositoryOperationsOutputPort dayPhaseLogRepositoryOps;
    Dice dice;
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
                playerId = PlayerId.of(playerOps.currentPlayerId());
                startingScene = SceneId.of(seed.getStartingSceneId());
                player = Player.builder()
                        .id(playerId)
                        .currentScene(startingScene)
                        .hitPoints(HitPoints.full(seed.getPlayerMaxHitPoints()))
                        .version(0)
                        .build();
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
            // template validates its descriptions, its spawn rule when present (valid chance, non-negative
            // tries, at least one candidate scene — an absent rule is authored absence: a contained-only item
            // never spawns onto the ground), and its containment odds up front, independent of how the spawn
            // and fill later roll. Declaring contains without the container capability is rejected here.
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

            // Checkpoint 8 — inter-template rule: every authored containment target resolves to an authored
            // item that is not itself a container (nesting is not authored in this slice — which also closes
            // the template-cycle hazard, where a contains loop would mint instances without bound). Resolved
            // against the authored set in memory and reported as a meaningful domain outcome, like the exit
            // and spawn-scene checks.
            Map<String, List<String>> invalidContainmentTargets = findInvalidContainmentTargets(authoredItems);
            if (!invalidContainmentTargets.isEmpty()) {
                presenter.presentItemContainmentTargetInvalid(invalidContainmentTargets);
                return;
            }

            // Checkpoint 9 — roll and place the item instances, then fill each spawned container instance
            // from its authored containment. Non-deterministic, but a pure in-memory construction with no
            // persistence side effect, so it runs outside the transaction.
            List<Item> spawnedItems = spawnItems(authoredItems);

            // Checkpoint 10 — construct the NPC templates from the authored NPCs (validity gate). Each template
            // validates its descriptions, its spawn rule, and its move chance up front, independent of how the
            // spawn later rolls — the item phase's up-front-gate discipline, applied to NPCs.
            List<AuthoredNpc> authoredNpcs;
            try {
                authoredNpcs = buildAuthoredNpcs(seed.getNpcs());
            } catch (InvalidDomainObjectError e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 11 — inter-aggregate rule: every NPC's candidate spawn scenes resolve to an authored
            // scene. Resolved in-memory against the world being built, like the exit and item-spawn checks.
            Map<String, List<SceneId>> unknownNpcSpawnScenes = findUnknownNpcSpawnScenes(authoredNpcs, scenes);
            if (!unknownNpcSpawnScenes.isEmpty()) {
                presenter.presentNpcSpawnSceneUnknown(unknownNpcSpawnScenes);
                return;
            }

            // Checkpoint 12 — inter-template rule: every authored corpse ref resolves to an authored item
            // that is a container (a corpse holds its loot) and has no ground-spawn rule (the death drop is a
            // corpse's only placement route — a spawn rule would also lay corpses out at init). Resolved
            // against the authored item set in memory and reported as a meaningful domain outcome, like the
            // containment-target check. This gate runs on every boot, which is what lets the corpse-blueprint
            // port hand back valid-by-provenance templates at death time.
            Map<String, String> invalidCorpseRefs = findInvalidCorpseRefs(authoredNpcs, authoredItems);
            if (!invalidCorpseRefs.isEmpty()) {
                presenter.presentNpcCorpseRefInvalid(invalidCorpseRefs);
                return;
            }

            // Checkpoint 13 — roll and place the NPC instances, outside the transaction (a pure in-memory
            // construction with no persistence side effect, like item spawning).
            List<Npc> spawnedNpcs = spawnNpcs(authoredNpcs);

            // Checkpoint 14 — one outcome, one atomic unit. A single transaction seeds the world if it is
            // still empty, creates the player if none exists yet, spawns items if none were spawned yet, spawns
            // NPCs if none were spawned yet, creates the world clock at time zero if none exists yet, and seeds
            // the day-phase log at its sentinel if none exists yet; holding all these read-then-write guards in
            // one transaction stops a concurrent initialization from double-seeding, double-creating, or
            // double-spawning. Exactly one after-commit presentation reports the single success — carrying the
            // items and NPCs spawned this run (empty if already spawned) — and the interaction ends here, since
            // nothing runs past a presentation.
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
                List<Npc> reportedNpcs;
                if (npcOps.npcsAlreadySpawned()) {
                    reportedNpcs = List.of();
                } else {
                    spawnedNpcs.forEach(npcOps::saveNpc);
                    reportedNpcs = spawnedNpcs;
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
                txOps.doAfterCommit(() ->
                        presenter.presentGameInitialized(scenes, playerId, reportedItems, reportedNpcs));
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
                    .id(SceneId.of(entry.getId()))
                    .name(entry.getName())
                    .shortDescription(entry.getShortDescription())
                    .fullDescription(entry.getFullDescription())
                    .exits(entry.getExits().stream()
                            .map(exit -> new Exit(exit.getName(), SceneId.of(exit.getTarget())))
                            .toList())
                    .miniGames(buildMiniGames(entry.getMiniGames()))
                    .build());
        }
        return scenes;
    }

    private static Set<MiniGame> buildMiniGames(List<String> authoredNames) {
        // A carrier may arrive with no mini-games key at all (null) — authored absence, not invalid input.
        // Each present name passes the closed-vocabulary gate, so an unknown game fails this checkpoint.
        if (authoredNames == null) {
            return Set.of();
        }
        return authoredNames.stream().map(MiniGame::fromAuthoredName).collect(Collectors.toSet());
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
            // An absent spawn rule is authored absence, not invalid input: a contained-only item never
            // spawns onto the ground and appears exclusively through a container's containment rolls.
            SpawnRule rule = null;
            SpawnEntry spawn = entry.getSpawn();
            if (spawn != null) {
                Chance chance = new Chance(spawn.getChanceNumerator(), spawn.getChanceDenominator());
                List<SceneId> candidateScenes = spawn.getScenes().stream().map(SceneId::of).toList();
                rule = new SpawnRule(chance, spawn.getMax(), candidateScenes);
            }
            // Portability resolves at the gate: an authored `portable` wins; unauthored defaults by kind —
            // a plain item is portable, a container is anchored (a transportable container, contents riding
            // along by reference, is an explicit authored fact, never an accident). The model stores the
            // resolved inverse (`anchored`, false = carryable, the safe builder default).
            boolean anchored = entry.getPortable() != null ? !entry.getPortable() : entry.isContainer();
            ItemTemplate template = new ItemTemplate(entry.getShortDescription(), entry.getFullDescription(),
                    entry.isContainer(), anchored, rule);
            authored.add(new AuthoredItem(entry.getId(), template, buildContainments(entry)));
        }
        return authored;
    }

    /**
     * Constructs one authored item's containment at the validity gate: each {@code contains} entry's odds
     * become a {@link Chance} up front, independent of how the fill later rolls — the template discipline
     * (reject invalid authoring even if no roll would ever exercise it). Declaring {@code contains} without
     * the container capability is rejected here too: a per-entry authoring shape violation, like an NPC
     * without a spawn rule. Whether each target ref <em>resolves</em> is the inter-template checkpoint's
     * business, not this gate's.
     */
    private static List<Containment> buildContainments(ItemEntry entry) {
        List<ContainsEntry> contains = entry.getContains() == null ? List.of() : entry.getContains();
        if (!contains.isEmpty() && !entry.isContainer()) {
            throw new InvalidDomainObjectError(
                    "item '%s' declares contains but is not a container".formatted(entry.getId()));
        }
        List<Containment> containments = new ArrayList<>(contains.size());
        for (ContainsEntry containsEntry : contains) {
            containments.add(new Containment(containsEntry.getItem(),
                    new Chance(containsEntry.getChanceNumerator(), containsEntry.getChanceDenominator())));
        }
        return containments;
    }

    private static Map<String, List<String>> findInvalidContainmentTargets(List<AuthoredItem> authoredItems) {
        Map<String, AuthoredItem> byAuthoredId = mapByAuthoredId(authoredItems);
        Map<String, List<String>> invalid = new LinkedHashMap<>();
        for (AuthoredItem item : authoredItems) {
            List<String> offending = new ArrayList<>();
            for (Containment containment : item.getContainments()) {
                AuthoredItem target = byAuthoredId.get(containment.getTargetRef());
                if (target == null || target.isContainer()) {
                    offending.add(containment.getTargetRef());
                }
            }
            if (!offending.isEmpty()) {
                invalid.put(item.getAuthoredId(), offending);
            }
        }
        return invalid;
    }

    private static Map<String, AuthoredItem> mapByAuthoredId(List<AuthoredItem> authoredItems) {
        Map<String, AuthoredItem> byAuthoredId = new LinkedHashMap<>();
        for (AuthoredItem item : authoredItems) {
            byAuthoredId.putIfAbsent(item.getAuthoredId(), item);
        }
        return byAuthoredId;
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
        Map<String, AuthoredItem> byAuthoredId = mapByAuthoredId(authoredItems);
        List<Item> spawned = new ArrayList<>();
        for (AuthoredItem item : authoredItems) {
            List<Item> instances = item.spawnInto(dice);
            spawned.addAll(instances);
            // Fill each spawned container instance from its authored containment: one roll per contains
            // entry per instance, so two chests roll their contents independently. Every target resolved at
            // the containment checkpoint, so the lookup cannot miss.
            for (Item instance : instances) {
                for (Containment containment : item.getContainments()) {
                    byAuthoredId.get(containment.getTargetRef())
                            .spawnInside(dice, containment.getChance(), instance.getId())
                            .ifPresent(spawned::add);
                }
            }
        }
        return spawned;
    }

    private static List<AuthoredNpc> buildAuthoredNpcs(List<NpcEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        List<AuthoredNpc> authored = new ArrayList<>(entries.size());
        for (NpcEntry entry : entries) {
            SpawnEntry spawn = entry.getSpawn();
            if (spawn == null) {
                throw new InvalidDomainObjectError(
                        "npc '%s' has no spawn rule".formatted(entry.getId()));
            }
            Chance chance = new Chance(spawn.getChanceNumerator(), spawn.getChanceDenominator());
            List<SceneId> candidateScenes = spawn.getScenes().stream().map(SceneId::of).toList();
            SpawnRule rule = new SpawnRule(chance, spawn.getMax(), candidateScenes);
            Chance moveChance = new Chance(entry.getMoveChanceNumerator(), entry.getMoveChanceDenominator());
            Chance attackChance =
                    new Chance(entry.getAttackChanceNumerator(), entry.getAttackChanceDenominator());
            NpcTemplate template = new NpcTemplate(entry.getShortDescription(), entry.getFullDescription(),
                    rule, moveChance, attackChance, entry.getHitPoints(), entry.getCorpse());
            authored.add(new AuthoredNpc(entry.getId(), template));
        }
        return authored;
    }

    /**
     * The inter-template corpse-ref rule: a present ref must resolve to an authored item that is a container
     * and never ground-spawns. Keyed by the NPC's authoring id, mapped to its offending ref — one ref per NPC,
     * so a flat map (unlike the containment check's ref lists).
     */
    private static Map<String, String> findInvalidCorpseRefs(List<AuthoredNpc> authoredNpcs,
                                                             List<AuthoredItem> authoredItems) {
        Map<String, AuthoredItem> byAuthoredId = mapByAuthoredId(authoredItems);
        Map<String, String> invalid = new LinkedHashMap<>();
        for (AuthoredNpc npc : authoredNpcs) {
            String ref = npc.corpseRef();
            if (ref == null) {
                continue;   // authored absence — the NPC leaves no corpse
            }
            AuthoredItem target = byAuthoredId.get(ref);
            if (target == null || !target.isContainer() || target.spawnsOnGround()) {
                invalid.put(npc.getAuthoredId(), ref);
            }
        }
        return invalid;
    }

    private static Map<String, List<SceneId>> findUnknownNpcSpawnScenes(List<AuthoredNpc> authoredNpcs,
                                                                        List<Scene> scenes) {
        Set<SceneId> known = scenes.stream().map(Scene::getId).collect(Collectors.toSet());
        Map<String, List<SceneId>> unknown = new LinkedHashMap<>();
        for (AuthoredNpc npc : authoredNpcs) {
            List<SceneId> dangling = npc.candidateScenesNotIn(known);
            if (!dangling.isEmpty()) {
                unknown.put(npc.getAuthoredId(), dangling);
            }
        }
        return unknown;
    }

    private List<Npc> spawnNpcs(List<AuthoredNpc> authoredNpcs) {
        List<Npc> spawned = new ArrayList<>();
        for (AuthoredNpc npc : authoredNpcs) {
            spawned.addAll(npc.spawnInto(dice));
        }
        return spawned;
    }

    /**
     * Use-case-private pairing of an item's authoring handle (used for diagnostics — e.g. reporting an
     * unknown spawn scene — and as the key containment refs resolve against) with its always-valid
     * {@link ItemTemplate} and its gate-validated {@link Containment} declarations. The handle is not a
     * domain identity, so it stays out of the model. It forwards {@link #candidateScenesNotIn},
     * {@link #spawnInto} and {@link #spawnInside} to the template one level, so the use case tells the
     * holder rather than reaching through it into the template and rule: the application keeps only the
     * orchestration (looping authored items, holding the dice, collecting), while the whole spawn policy —
     * including minting each instance's id from the dice — stays on the model.
     */
    @Value
    private static class AuthoredItem {
        String authoredId;
        ItemTemplate template;

        /** The authored containment of this item — empty unless it is a container declaring contents. */
        List<Containment> containments;

        boolean isContainer() {
            return template.isContainer();
        }

        boolean spawnsOnGround() {
            return template.spawnsOnGround();
        }

        List<SceneId> candidateScenesNotIn(Set<SceneId> knownSceneIds) {
            return template.candidateScenesNotIn(knownSceneIds);
        }

        List<Item> spawnInto(Dice dice) {
            return template.spawnInto(dice);
        }

        Optional<Item> spawnInside(Dice dice, Chance chance, ItemId container) {
            return template.spawnInside(dice, chance, container);
        }
    }

    /**
     * Use-case-private form of one authored {@code contains} declaration after the validity gate: the
     * contained template's authoring handle paired with the validated appearance {@link Chance}. The handle
     * stays out of the model (not a domain identity); the odds are handed to the contained template as a
     * <em>value</em> at fill time — they are the container's authored fact, not the contained template's.
     */
    @Value
    private static class Containment {
        String targetRef;
        Chance chance;
    }

    /**
     * Use-case-private pairing of an NPC's authoring handle (used only for diagnostics — e.g. reporting an
     * unknown spawn scene) with its always-valid {@link NpcTemplate} — the NPC twin of {@link AuthoredItem}.
     * The handle is not a domain identity, so it stays out of the model. It forwards
     * {@link #candidateScenesNotIn} and {@link #spawnInto} to the template one level, so the use case tells
     * the holder rather than reaching through it into the template and rule.
     */
    @Value
    private static class AuthoredNpc {
        String authoredId;
        NpcTemplate template;

        String corpseRef() {
            return template.getCorpseRef();
        }

        List<SceneId> candidateScenesNotIn(Set<SceneId> knownSceneIds) {
            return template.candidateScenesNotIn(knownSceneIds);
        }

        List<Npc> spawnInto(Dice dice) {
            return template.spawnInto(dice);
        }
    }
}
