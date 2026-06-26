package com.github.gameclean.infrastructure.terminal.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.jline.reader.LineReader;
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
 * <p>Two write paths, for the two timing situations. {@link #write(AttributedStringBuilder)} goes through
 * {@link Terminal#writer()} for output rendered <em>synchronously between reads</em> — a player command's
 * response, where no {@code readLine} is in flight so there is no live prompt to disturb.
 * {@link #printAbove(AttributedStringBuilder)} goes through {@link LineReader#printAbove} for output produced
 * <em>asynchronously while a read is in flight</em> — the background time ticker announcing a day phase above
 * the live {@code game> } prompt. This is the Phase-3 seam design-notes §7 reserved: one line editor, N async
 * {@code printAbove} writers onto one terminal buffer.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Console {

    Terminal terminal;
    LineReader lineReader;

    public Console(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    /** Writes already-styled content as a line, then flushes. For output rendered between reads. */
    public void write(AttributedStringBuilder content) {
        content.toAttributedString().println(terminal);
        terminal.flush();
    }

    /**
     * Writes already-styled content <em>above</em> the live prompt, for asynchronous output produced while a
     * {@code readLine} is in flight (the time ticker). JLine redraws the prompt and the player's partial input
     * below the inserted line, so the announcement does not corrupt what they are typing.
     */
    public void printAbove(AttributedStringBuilder content) {
        lineReader.printAbove(content.toAttributedString());
    }

    /** Writes a line of plain text in the error style (red). */
    public void printError(String text) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).append(text);
        write(sb);
    }
}
