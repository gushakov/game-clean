package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.ExitEntry;
import com.github.gameclean.core.usecase.initialize.SceneEntry;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the authored world seed YAML into a list of {@link SceneEntry} DTOs. A deliberately dumb
 * boundary translator: it parses YAML into plain maps/lists and hand-maps them to the {@link
 * SceneEntry} / {@link ExitEntry} DTOs (which live in {@code core.usecase.initialize}, the
 * {@code InitializeGame} input-port contract). It knows
 * nothing of the domain model — no {@code Scene}, no {@code SceneId} — so the validity gate stays
 * where it belongs (the domain constructors, driven by the use case).
 *
 * <p>SnakeYAML is loaded with a {@link SafeConstructor}, which yields only standard Java types
 * (maps, lists, strings, numbers) and never instantiates arbitrary classes from YAML tags — the
 * recommended safe-loading mode in SnakeYAML 2.x. The explicit hand-mapping (rather than bean/
 * {@code loadAs} binding) keeps the translation visible and avoids any type-coercion machinery.
 *
 * <p>A {@code @Component} so the {@link GameSeeder} can inject it. Tests still instantiate it
 * directly with {@code new} to play the driving adapter's role in isolation — the two ways of
 * obtaining one coexist harmlessly because it is stateless.
 */
@Component
public class SceneYamlReader {

    /**
     * Parses the given YAML stream. The document is expected to have a top-level {@code scenes:}
     * sequence; each element maps the {@link SceneEntry} fields, with a nested {@code exits:}
     * sequence of {@link ExitEntry} fields.
     *
     * @return the authored scenes in file order (never null; empty if the {@code scenes} key is absent)
     */
    public List<SceneEntry> read(InputStream yamlStream) {
        Objects.requireNonNull(yamlStream, "yaml stream must not be null");

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(yamlStream);
        if (root == null || root.get("scenes") == null) {
            return List.of();
        }

        List<SceneEntry> scenes = new ArrayList<>();
        for (Map<String, Object> sceneNode : asListOfMaps(root.get("scenes"))) {
            scenes.add(toSceneEntry(sceneNode));
        }
        return scenes;
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
                exits);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object node) {
        return (List<Map<String, Object>>) node;
    }

    private static String asString(Object value) {
        // YAML scalars come back as Strings under SafeConstructor; trim block-scalar trailing
        // newlines so multi-line descriptions arrive clean.
        return value == null ? null : value.toString().strip();
    }
}
