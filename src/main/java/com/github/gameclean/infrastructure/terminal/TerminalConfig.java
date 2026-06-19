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
 * shutdown. With no asynchronous writers yet there is nothing to stop first; the ordered-shutdown
 * dance (stop clock / outbox threads <em>before</em> {@code Terminal.close()}, or {@code printAbove}
 * throws on a closed terminal) becomes a {@code SmartLifecycle} concern only when those threads
 * arrive (Phase 3).
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
    public Console console(Terminal terminal) {
        return new Console(terminal);
    }
}
