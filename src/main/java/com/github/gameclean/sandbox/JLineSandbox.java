package com.github.gameclean.sandbox;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Throw-away spike to get a feel for JLine — NOT part of the application.
 * <p>
 * Reads a line, echoes it back, stops on {@code bye}. Meanwhile two background
 * tasks fire "ambient" events (weather, time-of-day) on a schedule and push
 * them to the terminal via {@link LineReader#printAbove}, which prints above the
 * live prompt without corrupting whatever you are typing — a preview of the
 * asynchronous, loop-driven presentation we want later.
 * <p>
 * Run it from a real terminal (or enable "Emulate terminal in output console"
 * in the IntelliJ run config); otherwise JLine falls back to a dumb terminal
 * and you lose line editing, history and colours.
 */
public final class JLineSandbox {

    private static final String[] WEATHER =
            {"clear skies", "light rain", "rolling fog", "a thunderstorm"};
    private static final String[] TIME_OF_DAY =
            {"dawn", "midday", "dusk", "night"};

    public static void main(String[] args) throws IOException {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("game-clean")
                    .build();

            ScheduledExecutorService ambient = startAmbientEvents(reader);
            try {
                terminal.writer().println(
                        "game-clean JLine sandbox — type 'bye' to quit. "
                                + "Ambient events arrive while you type.");
                terminal.flush();

                while (true) {
                    String line;
                    try {
                        line = reader.readLine("game> ");
                    } catch (UserInterruptException e) { // Ctrl-C — ignore, keep going
                        continue;
                    } catch (EndOfFileException e) {      // Ctrl-D — quit
                        break;
                    }

                    line = line.trim();
                    if ("bye".equalsIgnoreCase(line)) {
                        terminal.writer().println("Bye!");
                        terminal.flush();
                        break;
                    }

                    AttributedStringBuilder sb = new AttributedStringBuilder();
                    sb.append("you said: ")
                            .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                            .append(line);
                    sb.toAttributedString().println(terminal);
                    terminal.flush();
                }
            } finally {
                ambient.shutdownNow();
            }
        }
    }

    /**
     * Schedules the weather and time-of-day events on daemon threads so they
     * never hold the JVM open. Both push styled lines through
     * {@link LineReader#printAbove}, which is safe to call off the main thread.
     */
    private static ScheduledExecutorService startAmbientEvents(LineReader reader) {
        ThreadFactory daemons = runnable -> {
            Thread thread = new Thread(runnable, "ambient-events");
            thread.setDaemon(true);
            return thread;
        };
        ScheduledExecutorService ambient = Executors.newScheduledThreadPool(2, daemons);

        AtomicInteger weatherIndex = new AtomicInteger();
        ambient.scheduleAtFixedRate(() -> {
            String weather = WEATHER[weatherIndex.getAndIncrement() % WEATHER.length];
            reader.printAbove(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
                    .append("[weather] ")
                    .style(AttributedStyle.DEFAULT)
                    .append("the weather shifts to ")
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                    .append(weather)
                    .toAttributedString());
        }, 4, 4, TimeUnit.SECONDS);

        AtomicInteger timeIndex = new AtomicInteger();
        ambient.scheduleAtFixedRate(() -> {
            String time = TIME_OF_DAY[timeIndex.getAndIncrement() % TIME_OF_DAY.length];
            reader.printAbove(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA).bold())
                    .append("[time] ")
                    .style(AttributedStyle.DEFAULT)
                    .append("it is now ")
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                    .append(time)
                    .toAttributedString());
        }, 6, 6, TimeUnit.SECONDS);

        return ambient;
    }
}
