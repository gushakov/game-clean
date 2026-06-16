package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import lombok.RequiredArgsConstructor;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders the outcomes of "operate from the acting player's current scene" — the
 * {@link OrientPlayerPresenterOutputPort} cluster — to the
 * shared console. It is the single home of that rendering, injected by both the {@code look} and
 * {@code move} presenter beans so the scene description and the two not-found messages are written one
 * way, not copied per use case.
 *
 * <p>This is the form chosen for sharing presentation between use cases: a shared <em>collaborator</em>
 * (composition), not a presenter base class (inheritance) and not a grab-bag presenter port. It is
 * domain-aware (it knows {@link Scene}) and delegates the actual terminal writing to the domain-agnostic
 * {@link Console}.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CurrentSceneRenderer {

    private final Console console;

    /**
     * Renders a scene: its name, full description, the sorted list of exit names, and — when any lie on the
     * ground — the items present, each on its own line by short description.
     */
    public void renderScene(Scene scene, List<Item> itemsOnGround) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
                .append(scene.getName())
                .style(AttributedStyle.DEFAULT)
                .append(System.lineSeparator())
                .append(scene.getFullDescription().strip())
                .append(System.lineSeparator())
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("Exits: ")
                .style(AttributedStyle.DEFAULT)
                .append(exitNames(scene));
        appendItemsOnGround(sb, itemsOnGround);
        console.write(sb);
    }

    /** The acting player does not exist — a configuration or data fault, surfaced plainly. */
    public void renderPlayerNotFound(PlayerId playerId) {
        console.printError("There is no player '%s'.".formatted(playerId.getValue()));
    }

    /** The player's recorded current scene resolves to nothing. */
    public void renderCurrentSceneNotFound(SceneId sceneId) {
        console.printError("You seem to be nowhere — scene '%s' does not exist.".formatted(sceneId.getValue()));
    }

    /**
     * Appends the items on the ground, each on its own line, sorted by short description for a stable
     * display order (the persisted collection is unordered, and several instances may share a description).
     * Nothing is appended when the ground is empty — the line appears only when there is something to see.
     */
    private static void appendItemsOnGround(AttributedStringBuilder sb, List<Item> itemsOnGround) {
        if (itemsOnGround.isEmpty()) {
            return;
        }
        sb.append(System.lineSeparator())
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("On the ground:")
                .style(AttributedStyle.DEFAULT);
        itemsOnGround.stream()
                .map(Item::getShortDescription)
                .sorted(Comparator.naturalOrder())
                .forEach(description -> sb.append(System.lineSeparator()).append("  ").append(description));
    }

    /** Exit names, sorted for a stable display order (the persisted collection is unordered). */
    private static String exitNames(Scene scene) {
        if (scene.getExits().isEmpty()) {
            return "none";
        }
        return scene.getExits().stream()
                .map(Exit::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", "));
    }
}
