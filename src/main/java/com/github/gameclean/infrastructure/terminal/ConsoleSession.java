package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.usecase.explore.LookInputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Primary (driving) adapter: the interactive player session — a blocking read loop on the main thread
 * that drives the application from the console. It is a thin <em>controller</em>: read a line, ask the
 * {@link CommandParser} for the player's intent, and either control the loop ({@code bye}) or delegate
 * to a use case. It carries no game logic — that lives in the use cases, which present their own
 * output (the console no longer touches a presenter).
 *
 * <p>{@link #start()} blocks until {@code bye}. It is invoked by
 * {@link com.github.gameclean.infrastructure.BootSequence} <em>after</em> the world and player have
 * been seeded. This is a plain singleton, not an {@code ApplicationRunner}: the boot order is stated
 * explicitly in {@code BootSequence}.
 *
 * <p>For each {@code look}, a <strong>fresh prototype</strong> {@link LookInputPort} is pulled from
 * the {@link ApplicationContext} — the cargo-clean reference idiom: a singleton adapter fetches the
 * prototype use case per interaction rather than holding one (which would silently defeat the scope).
 * The player id the controller hands inward is the single configured player ({@code game.player.id});
 * value-object construction happens inside the use case.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ConsoleSession {

    private final LineReader lineReader;
    private final CommandParser commandParser;
    private final ApplicationContext applicationContext;
    private final GameConfigurationProperties properties;

    public void start() {
        printLine("Welcome to game-clean. Commands: 'look', 'bye'.");
        while (true) {
            String line;
            try {
                line = lineReader.readLine("game> ");
            } catch (UserInterruptException e) { // Ctrl-C — ignore, keep playing
                continue;
            } catch (EndOfFileException e) {      // Ctrl-D — quit
                break;
            }

            Optional<Command> parsed = commandParser.parse(line);
            if (parsed.isEmpty()) {               // blank line — nothing to do
                continue;
            }
            Command command = parsed.get();
            if (command instanceof QuitCommand) {
                printLine("Bye!");
                break;
            } else if (command instanceof LookCommand) {
                look();
            } else if (command instanceof UnknownCommand unknown) {
                printLine("Unknown command: '%s'. Try 'look' or 'bye'.".formatted(unknown.getInput()));
            }
        }
    }

    private void look() {
        // Pull a fresh prototype use case per interaction; it presents its own outcome.
        LookInputPort lookUseCase = applicationContext.getBean(LookInputPort.class);
        lookUseCase.look(properties.getPlayer().getId());
    }

    private void printLine(String text) {
        lineReader.getTerminal().writer().println(text);
        lineReader.getTerminal().flush();
    }
}
