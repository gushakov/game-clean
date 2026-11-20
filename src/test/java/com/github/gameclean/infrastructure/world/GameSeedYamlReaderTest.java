package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Lock;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.seed.ContainsEntry;
import com.github.gameclean.core.port.seed.ExitEntry;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.core.port.seed.NpcEntry;
import com.github.gameclean.core.port.seed.SceneEntry;
import com.github.gameclean.core.port.seed.SpawnEntry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Spike test for the YAML → domain edge: it proves the authored seed parses into the {@code *Entry} DTOs
 * (scenes, exits, items and their spawn rules) and that the scene entries construct <em>valid</em>
 * {@link Scene} aggregates.
 *
 * <p>The entry → aggregate mapping lives <em>in the test</em>, not in the reader: constructing the value
 * objects is the use case's job, and the reader must stay free of domain types. The reader does, however,
 * own the authoring-syntax split (a {@code "scn2, scn3"} list, a {@code "12/50"} fraction), which the item
 * assertions below pin. DB-free, so it runs under Surefire.
 */
class GameSeedYamlReaderTest {

    private final GameSeedYamlReader reader = new GameSeedYamlReader();

    @Test
    void readsAllAuthoredScenesInOrderWithTheirExits() {
        List<SceneEntry> scenes = readSeed().getScenes();

        assertThat(scenes).extracting(SceneEntry::getId)
                .containsExactly("scn1", "scn2", "scn3", "scn4", "scn5", "scn6");

        SceneEntry gate = scenes.getFirst();
        assertThat(gate.getName()).isEqualTo("Old Gate");
        assertThat(gate.getShortDescription()).isEqualTo("A weathered stone archway.");
        assertThat(gate.getFullDescription()).isNotBlank();
        assertThat(gate.getExits())
                .containsExactly(new ExitEntry("north", "scn2", null, null), new ExitEntry("east", "scn3", null, null));
    }

    @Test
    void readsAuthoredMiniGamesSplittingTheCommaListAndDefaultingToNone() {
        List<SceneEntry> scenes = readSeed().getScenes();

        // scn3 (the Armoury) authors `mini-games: blackjack`; every other scene omits the key.
        assertThat(scenes.get(2).getMiniGames()).containsExactly("blackjack");
        assertThat(scenes.getFirst().getMiniGames()).isEmpty();
    }

    @Test
    void readsAuthoredItemsWithTheirSpawnRulesSplittingTheAuthoringSyntax() {
        List<ItemEntry> items = readSeed().getItems();

        assertThat(items).extracting(ItemEntry::getId).containsExactly("itm1", "itm2", "itm3", "itm4", "itm5", "itm6");

        ItemEntry dagger = items.getFirst();
        assertThat(dagger.getShortDescription()).isEqualTo("A rusty dagger.");
        assertThat(dagger.getFullDescription()).isNotBlank();

        SpawnEntry spawn = dagger.getSpawn();
        assertThat(spawn.getScenes()).containsExactly("scn2", "scn3");   // "scn2, scn3" split into a list
        assertThat(spawn.getChanceNumerator()).isEqualTo(12);            // "12/50" split into a fraction
        assertThat(spawn.getChanceDenominator()).isEqualTo(50);
        assertThat(spawn.getMax()).isEqualTo(3);
    }

    @Test
    void readsAuthoredContainmentSplittingTheChanceAndDefaultingToAbsence() {
        List<ItemEntry> items = readSeed().getItems();

        // itm4 (the oak chest) authors `container: true` and one contains entry; every other item omits both,
        // so the flag defaults false and the contains list stays null (authored absence, like an absent spawn).
        ItemEntry chest = items.get(3);
        assertThat(chest.isContainer()).isTrue();
        assertThat(chest.getContains()).containsExactly(new ContainsEntry("itm1", 1, 45));
        // `portable` is unauthored on the chest — the carrier keeps the absence visible (null, not false),
        // because the gate applies the kind-sensitive default (a container is anchored unless authored).
        assertThat(chest.getPortable()).isNull();

        ItemEntry dagger = items.getFirst();
        assertThat(dagger.isContainer()).isFalse();
        assertThat(dagger.getContains()).isNull();
        assertThat(dagger.getPortable()).isNull();
    }

    @Test
    void readsTheAuthoredSayingsOnNpcsInOrder() {
        GameSeed seed = readSeed();

        // npc1 (the hooded wanderer) authors three lines; they arrive raw, trimmed, in authored order (#104).
        assertThat(seed.getNpcs().getFirst())
                .satisfies(npc -> assertThat(npc.getSayings()).containsExactly(
                        "You walk the same stones I walked. Mind where they lead.",
                        "The gate was shut long before the hinges rusted. Ask yourself by whom.",
                        "I have counted the steps to the tower a thousand times. The number changes."));
    }

    @Test
    void readsTheAuthoredCorpseRefOnNpcsDefaultingToAbsence() {
        GameSeed seed = readSeed();

        // npc1 (the hooded wanderer) authors `corpse: itm5` — carried through as the raw authoring handle; npc2 (the
        // innkeeper) authors none.
        assertThat(seed.getNpcs()).extracting(NpcEntry::getCorpse).containsExactly("itm5", null);

        // The corpse template itself is an ordinary items entry: a container with NO spawn rule (the death
        // drop is its only placement route) and no `portable` (a container defaults to anchored).
        ItemEntry corpse = seed.getItems().get(4);
        assertThat(corpse.getId()).isEqualTo("itm5");
        assertThat(corpse.isContainer()).isTrue();
        assertThat(corpse.getSpawn()).isNull();
        assertThat(corpse.getPortable()).isNull();
        assertThat(corpse.getContains()).containsExactly(new ContainsEntry("itm2", 1, 2));
    }

    @Test
    void readsTheAuthoredLockOnExitsAndTheOpensHandleOnItemsDefaultingToAbsence() {
        GameSeed seed = readSeed();

        // scn5 (the inn) authors a locked stair up with its refusal line, and an open way back east.
        SceneEntry inn = seed.getScenes().get(4);
        assertThat(inn.getExits()).containsExactly(
                new ExitEntry("east", "scn2", null, null),
                new ExitEntry("up", "scn6", "guest-room", "The door at the head of the stair is locked."));
        // scn6 (the guest room) authors its way back down without a lock: locks are per exit.
        assertThat(seed.getScenes().get(5).getExits())
                .containsExactly(new ExitEntry("down", "scn5", null, null));

        // itm6 (the brass key) authors `opens: guest-room`; every other item omits the key.
        assertThat(seed.getItems()).extracting(ItemEntry::getId, ItemEntry::getOpens).containsExactly(
                tuple("itm1", null), tuple("itm2", null), tuple("itm3", null), tuple("itm4", null),
                tuple("itm5", null), tuple("itm6", "guest-room"));
    }

    @Test
    void carriesTheConfiguredStartingSceneIdThrough() {
        assertThat(readSeed().getStartingSceneId()).isEqualTo("scn1");
    }

    @Test
    void entriesConstructValidSceneAggregatesWiringTheGraphAsAuthored() {
        List<SceneEntry> entries = readSeed().getScenes();

        // The mapping the use case will own: primitives in, value objects + aggregates out.
        assertThatCode(() -> entries.forEach(GameSeedYamlReaderTest::toScene))
                .doesNotThrowAnyException();

        Map<SceneId, Scene> world = entries.stream()
                .map(GameSeedYamlReaderTest::toScene)
                .collect(Collectors.toMap(Scene::getId, Function.identity()));

        assertThat(world).hasSize(6);

        Scene gate = world.get(SceneId.of("scn1"));
        assertThat(gate.getName()).isEqualTo("Old Gate");
        assertThat(gate.getExits())
                .containsExactlyInAnyOrder(
                        new Exit("north", SceneId.of("scn2")),
                        new Exit("east", SceneId.of("scn3")));

        // Every exit target resolves to an authored scene — the spike keeps the graph coherent;
        // the dangling-target -> domain-error case belongs to InitializeGame's two-pass validation.
        assertThat(world.values()).allSatisfy(scene ->
                scene.getExits().forEach(exit -> assertThat(world).containsKey(exit.getTarget())));
    }

    private GameSeed readSeed() {
        try (InputStream in = getClass().getResourceAsStream("/world/scenes.yaml")) {
            assertThat(in).as("world/scenes.yaml on the classpath").isNotNull();
            return reader.read(in, "scn1", 30);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read world seed", e);
        }
    }

    private static Scene toScene(SceneEntry entry) {
        return Scene.builder()
                .id(SceneId.of(entry.getId()))
                .name(entry.getName())
                .shortDescription(entry.getShortDescription())
                .fullDescription(entry.getFullDescription())
                .exits(entry.getExits().stream()
                        .map(e -> new Exit(e.getName(), SceneId.of(e.getTarget()),
                                e.getLock() == null ? null : new Lock(e.getLock(), e.getLockedDescription())))
                        .toList())
                .build();
    }
}
