package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import lombok.RequiredArgsConstructor;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Secondary (driven) adapter that renders a {@link Scene} to the shared JLine terminal — the
 * console-facing peer of the seeder's {@code LoggingConstructWorldPresenter}. It is a distinct bean
 * from the input loop (opposite direction of the hexagon) but shares the same {@link Terminal}
 * resource injected from {@link TerminalConfig}.
 *
 * <p><strong>Spike scope.</strong> There is no presenter <em>port</em> yet: it emerges with the real
 * {@code look} use case, which will own it. For now the input loop calls this concrete adapter
 * directly, standing in for the use-case orchestration to come.
 *
 * <p>Writes go through {@link Terminal#writer()} because every render happens synchronously between
 * reads — no {@code readLine} is in flight, so there is no live prompt to write above. The switch to
 * {@link org.jline.reader.LineReader#printAbove} is the known seam for Phase 3, when an asynchronous
 * clock / outbox relay can emit while the player is mid-keystroke.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TerminalScenePresenter {

    private final Terminal terminal;

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
        sb.toAttributedString().println(terminal);
        terminal.flush();
    }

    private static String exitNames(Scene scene) {
        if (scene.getExits().isEmpty()) {
            return "none";
        }
        return scene.getExits().stream().map(Exit::getName).collect(Collectors.joining(", "));
    }
}
