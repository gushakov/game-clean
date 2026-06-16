package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.seed.ExitEntry;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.core.port.seed.SceneEntry;
import com.github.gameclean.core.port.seed.SpawnEntry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
                .containsExactly("scn1", "scn2", "scn3", "scn4");

        SceneEntry gate = scenes.getFirst();
        assertThat(gate.getName()).isEqualTo("Old Gate");
        assertThat(gate.getShortDescription()).isEqualTo("A weathered stone archway.");
        assertThat(gate.getFullDescription()).isNotBlank();
        assertThat(gate.getExits())
                .containsExactly(new ExitEntry("north", "scn2"), new ExitEntry("east", "scn3"));
    }

    @Test
    void readsAuthoredItemsWithTheirSpawnRulesSplittingTheAuthoringSyntax() {
        List<ItemEntry> items = readSeed().getItems();

        assertThat(items).extracting(ItemEntry::getId).containsExactly("itm1", "itm2", "itm3");

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

        assertThat(world).hasSize(4);

        Scene gate = world.get(new SceneId("scn1"));
        assertThat(gate.getName()).isEqualTo("Old Gate");
        assertThat(gate.getExits())
                .containsExactlyInAnyOrder(
                        new Exit("north", new SceneId("scn2")),
                        new Exit("east", new SceneId("scn3")));

        // Every exit target resolves to an authored scene — the spike keeps the graph coherent;
        // the dangling-target -> domain-error case belongs to InitializeGame's two-pass validation.
        assertThat(world.values()).allSatisfy(scene ->
                scene.getExits().forEach(exit -> assertThat(world).containsKey(exit.getTarget())));
    }

    private GameSeed readSeed() {
        try (InputStream in = getClass().getResourceAsStream("/world/scenes.yaml")) {
            assertThat(in).as("world/scenes.yaml on the classpath").isNotNull();
            return reader.read(in, "scn1");
        } catch (Exception e) {
            throw new IllegalStateException("failed to read world seed", e);
        }
    }

    private static Scene toScene(SceneEntry entry) {
        return Scene.builder()
                .id(new SceneId(entry.getId()))
                .name(entry.getName())
                .shortDescription(entry.getShortDescription())
                .fullDescription(entry.getFullDescription())
                .exits(entry.getExits().stream()
                        .map(e -> new Exit(e.getName(), new SceneId(e.getTarget())))
                        .toList())
                .build();
    }
}
