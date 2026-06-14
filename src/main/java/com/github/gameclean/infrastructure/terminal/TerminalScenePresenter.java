package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.explore.LookPresenterOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Secondary (driven) adapter that renders the {@code Look} use case's outcomes to the shared JLine
 * terminal — the console-facing peer of the seeder's {@code LoggingConstructWorldPresenter}. It is a
 * distinct bean from the input loop (opposite direction of the hexagon) but shares the same
 * {@link Terminal} resource injected from {@link TerminalConfig}.
 *
 * <p>It implements {@link LookPresenterOutputPort}. The name stays deliberately scene-centric: scene
 * rendering ({@link #presentScene}) is the capability a future {@code move} use case will want to
 * share, so this bean is the natural seed of that shared renderer — but the port stays co-located with
 * {@code Look} until {@code move} actually exists to shape the extraction.
 *
 * <p>Writes go through {@link Terminal#writer()} because every render happens synchronously between
 * reads — no {@code readLine} is in flight, so there is no live prompt to write above. The switch to
 * {@link org.jline.reader.LineReader#printAbove} is the known seam for Phase 3.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TerminalScenePresenter implements LookPresenterOutputPort {

    private final Terminal terminal;

    @Override
    public void presentScene(Scene scene) {
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
        print(sb);
    }

    @Override
    public void presentPlayerNotFound(PlayerId playerId) {
        printError("There is no player '%s'.".formatted(playerId.getValue()));
    }

    @Override
    public void presentCurrentSceneNotFound(SceneId sceneId) {
        printError("You seem to be nowhere — scene '%s' does not exist.".formatted(sceneId.getValue()));
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Look] Unexpected error", e);
        printError("Something went wrong. Please try again.");
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

    private void printError(String text) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).append(text);
        print(sb);
    }

    private void print(AttributedStringBuilder sb) {
        sb.toAttributedString().println(terminal);
        terminal.flush();
    }
}
