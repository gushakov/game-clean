package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.port.seed.ExitEntry;
import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.core.port.seed.NpcEntry;
import com.github.gameclean.core.port.seed.SceneEntry;
import com.github.gameclean.core.port.seed.SpawnEntry;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the authored seed YAML and assembles the {@link GameSeed} carrier. A deliberately dumb boundary
 * translator: it parses YAML into plain maps/lists and hand-maps them to the {@link SceneEntry} /
 * {@link ExitEntry} / {@link ItemEntry} / {@link SpawnEntry} DTOs (which live in {@code core.port.seed},
 * the seed-source port's return contract). It knows nothing of the domain model — no {@code Scene}, no
 * {@code ItemId} — so the validity gate stays where it belongs (the domain constructors, driven by the
 * use case).
 *
 * <p>It does, however, normalize the <em>authoring syntax</em> the way a parser should: a {@code "scn2, scn3"}
 * spawn-scene list is split into entries, and a {@code "12/50"} chance fraction is split into its numerator
 * and denominator. That split is syntactic — the resulting numbers may still be <em>domain</em>-invalid
 * (e.g. a zero denominator), which the use-case gate rejects as a presented outcome. A fraction that is not
 * two integers is a malformed authored file and fails fast here, like malformed YAML.
 *
 * <p>The scenes and items come from the file; the starting scene id is supplied by the caller (from
 * configuration). SnakeYAML is loaded with a {@link SafeConstructor}, the recommended safe-loading mode.
 *
 * <p>A {@code @Component} so the {@link GameSeeder} can inject it; tests instantiate it directly with
 * {@code new} to play the driving adapter's role in isolation — both work because it is stateless.
 */
@Component
public class GameSeedYamlReader {

    /**
     * Parses the given YAML stream and assembles the full game seed. The document may carry top-level
     * {@code scenes:}, {@code items:} and {@code npcs:} sequences; any absent yields an empty list.
     *
     * @param yamlStream         the authored seed document
     * @param startingSceneId    the configured starting scene id (not read from the file)
     * @param playerMaxHitPoints the configured player's max hit points (not read from the file)
     * @return the assembled {@link GameSeed} (never null)
     */
    public GameSeed read(InputStream yamlStream, String startingSceneId, int playerMaxHitPoints) {
        Objects.requireNonNull(yamlStream, "yaml stream must not be null");

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(yamlStream);

        return new GameSeed(
                parseScenes(root), startingSceneId, playerMaxHitPoints, parseItems(root), parseNpcs(root));
    }

    private static List<SceneEntry> parseScenes(Map<String, Object> root) {
        if (root == null || root.get("scenes") == null) {
            return List.of();
        }
        List<SceneEntry> scenes = new ArrayList<>();
        for (Map<String, Object> sceneNode : asListOfMaps(root.get("scenes"))) {
            scenes.add(toSceneEntry(sceneNode));
        }
        return scenes;
    }

    private static List<ItemEntry> parseItems(Map<String, Object> root) {
        if (root == null || root.get("items") == null) {
            return List.of();
        }
        List<ItemEntry> items = new ArrayList<>();
        for (Map<String, Object> itemNode : asListOfMaps(root.get("items"))) {
            items.add(toItemEntry(itemNode));
        }
        return items;
    }

    private static List<NpcEntry> parseNpcs(Map<String, Object> root) {
        if (root == null || root.get("npcs") == null) {
            return List.of();
        }
        List<NpcEntry> npcs = new ArrayList<>();
        for (Map<String, Object> npcNode : asListOfMaps(root.get("npcs"))) {
            npcs.add(toNpcEntry(npcNode));
        }
        return npcs;
    }

    private static SceneEntry toSceneEntry(Map<String, Object> node) {
        List<ExitEntry> exits = new ArrayList<>();
        Object exitsNode = node.get("exits");
        if (exitsNode != null) {
            for (Map<String, Object> exitNode : asListOfMaps(exitsNode)) {
                exits.add(new ExitEntry(asString(exitNode.get("name")), asString(exitNode.get("target"))));
            }
        }
        return new SceneEntry(
                asString(node.get("id")),
                asString(node.get("name")),
                asString(node.get("shortDescription")),
                asString(node.get("fullDescription")),
                exits,
                parseCommaList(asString(node.get("mini-games"))));
    }

    private static ItemEntry toItemEntry(Map<String, Object> node) {
        Object spawnNode = node.get("spawn");
        SpawnEntry spawn = spawnNode == null ? null : toSpawnEntry(asMap(spawnNode));
        return new ItemEntry(
                asString(node.get("id")),
                asString(node.get("shortDescription")),
                asString(node.get("fullDescription")),
                spawn);
    }

    private static NpcEntry toNpcEntry(Map<String, Object> node) {
        Object spawnNode = node.get("spawn");
        SpawnEntry spawn = spawnNode == null ? null : toSpawnEntry(asMap(spawnNode));
        int[] moveChance = parseChance(asString(node.get("moveChance")));
        int[] attackChance = parseChance(asString(node.get("attackChance")));
        return new NpcEntry(
                asString(node.get("id")),
                asString(node.get("shortDescription")),
                asString(node.get("fullDescription")),
                spawn,
                moveChance[0],
                moveChance[1],
                attackChance[0],
                attackChance[1],
                asInt(node.get("hitPoints"), "npc hit points"));
    }

    private static SpawnEntry toSpawnEntry(Map<String, Object> node) {
        List<String> scenes = parseCommaList(asString(node.get("scenes")));
        int[] chance = parseChance(asString(node.get("chance")));
        int max = asInt(node.get("max"), "spawn max");
        return new SpawnEntry(scenes, chance[0], chance[1], max);
    }

    /**
     * Splits a comma-separated authoring list ({@code "scn2, scn3"} spawn scenes, {@code "blackjack"}
     * mini-games) into trimmed, non-empty entries; null/blank yields an empty list.
     */
    private static List<String> parseCommaList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    /** Splits a {@code "12/50"} authoring fraction into {@code [numerator, denominator]}. */
    private static int[] parseChance(String value) {
        String[] parts = Objects.requireNonNull(value, "chance must not be null").split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "malformed chance '%s'; expected 'numerator/denominator'".formatted(value));
        }
        return new int[]{Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip())};
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object node) {
        return (List<Map<String, Object>>) node;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        return (Map<String, Object>) node;
    }

    private static int asInt(Object value, String what) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(Objects.requireNonNull(value, what + " must not be null").toString().strip());
    }

    private static String asString(Object value) {
        // YAML scalars come back as Strings under SafeConstructor; trim block-scalar trailing
        // newlines so multi-line descriptions arrive clean.
        return value == null ? null : value.toString().strip();
    }
}
