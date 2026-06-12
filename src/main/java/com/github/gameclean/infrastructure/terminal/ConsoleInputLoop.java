package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.infrastructure.persistence.scene.SceneDbEntity;
import com.github.gameclean.infrastructure.persistence.scene.SceneDbEntityMapper;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Primary (driving) adapter: a blocking read loop on the main thread that drives the application
 * from the console. It injects the shared {@link LineReader} from {@link TerminalConfig} and, once a
 * command is read, hands the result to the driven {@link TerminalScenePresenter} — the two adapters
 * meeting over one shared terminal.
 *
 * <p>It runs as an {@link ApplicationRunner} ordered <em>after</em> {@code WorldSeedRunner}: this loop
 * blocks until {@code bye}, so were it to run first the seeder would never execute and {@code look}
 * would find an empty world. Once it returns, {@code SpringApplication.run} completes and — this being
 * a non-web application — the JVM exits.
 *
 * <p><strong>Spike scope.</strong> There is no {@code look} use case yet, so this driving adapter
 * loads the entry scene <em>directly</em> through the infrastructure {@link SceneSpringDataRepository}
 * and {@link SceneDbEntityMapper}, deliberately bypassing the clean {@code SceneRepositoryOperationsOutputPort}
 * (which has no read method until a use case defines that contract). When the real use case lands, the
 * loop will instead pull a prototype input port and the orchestration — load, then present — moves
 * inward. {@code look} always loads the same fixed entry scene; a real "current scene" belongs to the
 * {@code Player} aggregate, which this spike does not model.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class ConsoleInputLoop implements ApplicationRunner {

    /** Spike shortcut: {@code look} always loads the seed's entry scene (Old Gate). */
    private static final String ENTRY_SCENE_ID = "scn1";

    private final LineReader lineReader;
    private final SceneSpringDataRepository sceneRepository;
    private final SceneDbEntityMapper mapper;
    private final TerminalScenePresenter scenePresenter;

    @Override
    public void run(ApplicationArguments args) {
        printLine("Welcome to game-clean. Commands: 'look', 'bye'.");
        while (true) {
            String command;
            try {
                command = lineReader.readLine("game> ");
            } catch (UserInterruptException e) { // Ctrl-C — ignore, keep playing
                continue;
            } catch (EndOfFileException e) {      // Ctrl-D — quit
                break;
            }

            command = command == null ? "" : command.strip();
            if ("bye".equalsIgnoreCase(command)) {
                printLine("Bye!");
                break;
            } else if ("look".equalsIgnoreCase(command)) {
                look();
            } else if (!command.isEmpty()) {
                printLine("Unknown command: '%s'. Try 'look' or 'bye'.".formatted(command));
            }
        }
    }

    private void look() {
        Optional<SceneDbEntity> entity = sceneRepository.findById(ENTRY_SCENE_ID);
        if (entity.isEmpty()) {
            printLine("There is nothing here yet — the world has not been seeded.");
            return;
        }
        Scene scene = mapper.toDomain(entity.get());
        scenePresenter.presentScene(scene);
    }

    private void printLine(String text) {
        lineReader.getTerminal().writer().println(text);
        lineReader.getTerminal().flush();
    }
}
