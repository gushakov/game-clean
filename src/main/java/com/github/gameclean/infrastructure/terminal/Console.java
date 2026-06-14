package com.github.gameclean.infrastructure.terminal;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Thin facade over the shared JLine {@link Terminal} for styled output — the single place that knows how
 * to write to the console. It is an infrastructure <em>resource</em> (declared in {@link TerminalConfig}
 * alongside the {@code Terminal} and {@code LineReader}), not an adapter: the driven presenter beans
 * inject it to render their outcomes, so the JLine writing vocabulary lives in one place rather than being
 * copied into every presenter. This is the facade design-notes §7 reserved for "a second use site"; the
 * {@code move} use case is that site.
 *
 * <p>It is deliberately <b>domain-agnostic</b> — it knows styled text and the terminal, not scenes or
 * players. Rendering a domain object (a {@link com.github.gameclean.core.model.scene.Scene}) is presenter
 * logic that builds an {@link AttributedStringBuilder} and hands it here to write.
 *
 * <p>Writes go through {@link Terminal#writer()} because rendering happens synchronously between reads —
 * no {@code readLine} is in flight, so there is no live prompt to print above. The switch to
 * {@link org.jline.reader.LineReader#printAbove} is the known Phase-3 seam.
 */
public class Console {

    private final Terminal terminal;

    public Console(Terminal terminal) {
        this.terminal = terminal;
    }

    /** Writes already-styled content as a line, then flushes. */
    public void write(AttributedStringBuilder content) {
        content.toAttributedString().println(terminal);
        terminal.flush();
    }

    /** Writes a line of plain text in the error style (red). */
    public void printError(String text) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).append(text);
        write(sb);
    }
}
