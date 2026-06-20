package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.infrastructure.terminal.render.Console;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Declares the JLine {@link Terminal} and {@link LineReader} as shared singleton <em>infrastructure
 * resources</em> — deliberately <strong>not</strong> adapters. Exactly one physical console exists,
 * and both the driving adapter (the input loop, calling {@link LineReader#readLine}) and the driven
 * adapter (a presenter, writing to {@link Terminal#writer()}) need it. Promoting the primitives to
 * their own beans lets two genuinely separate adapters — opposite directions of the hexagon — share
 * one resource without either owning it, so the "two adapters cannot be the same bean" rule holds
 * while the terminal is reused. The application layer never sees JLine: it crosses only through
 * input / output <em>ports</em>.
 *
 * <p>Guarded by {@code game.terminal.enabled} (default behaviour: present in the application profile,
 * absent in test profiles) so that {@code @SpringBootTest} slices never try to grab a system terminal.
 *
 * <p>The {@link Terminal} is declared with {@code destroyMethod = "close"} so Spring closes it on
 * shutdown. The first asynchronous writer — the {@code GameClockTicker} announcing day phases via
 * {@code printAbove} — has now arrived, so the ordered-shutdown dance is live: the ticker is a
 * {@code @Scheduled} task, and Spring's scheduling is lifecycle-managed, so the container cancels scheduled
 * tasks (waiting for an in-flight run) at context close <em>before</em> it destroys plain singletons like
 * this {@link Terminal}. So the ticker stops (no more {@code printAbove}) before the terminal closes —
 * exactly the order design-notes §7 requires, and we get it without hand-numbering lifecycle phases.
 */
@Configuration
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
public class TerminalConfig {

    @Bean(destroyMethod = "close")
    public Terminal terminal() throws IOException {
        return TerminalBuilder.builder()
                .system(true)
                .build();
    }

    @Bean
    public LineReader lineReader(Terminal terminal) {
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("game-clean")
                .build();
    }

    /**
     * The styled-output facade over the {@link Terminal} — another shared infrastructure resource (not an
     * adapter), injected by the driven presenter beans so the JLine writing vocabulary lives in one place.
     */
    @Bean
    public Console console(Terminal terminal, LineReader lineReader) {
        return new Console(terminal, lineReader);
    }
}
