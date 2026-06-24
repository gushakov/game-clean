package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.usecase.clock.AskForTimeInputPort;
import com.github.gameclean.core.usecase.clock.SuspendGameInputPort;
import com.github.gameclean.core.usecase.explore.ExamineInputPort;
import com.github.gameclean.core.usecase.explore.LookInputPort;
import com.github.gameclean.core.usecase.explore.MoveInputPort;
import com.github.gameclean.core.usecase.guidance.GuidanceInputPort;
import com.github.gameclean.infrastructure.terminal.command.*;
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
 * {@link CommandParser} for the player's intent, and delegate to a use case ({@code look}, {@code examine},
 * {@code move}, {@code now}) and/or control the loop ({@code bye} both banks the session via the
 * {@code SuspendGame} use case and breaks the loop). Even an <em>unrecognized</em> line delegates: the parser
 * decides it matched no command, and the {@code Guidance} use case decides to steer the player and presents
 * the guidance. It carries no game logic — that lives in the use cases, which present their own output (the
 * console no longer touches a presenter; the only direct write left is the lifecycle welcome banner).
 *
 * <p>It also holds the one piece of conversational state the design admits: a pending {@code examine}
 * disambiguation offer, in the shared {@link AffordanceContext} resource. When {@code examine} finds an
 * ambiguous target its presenter arms that buffer with the numbered candidates; on a subsequent bare number
 * ({@link SelectCommand}) the console <em>only detects the selection intent</em> and delegates — it hands the
 * remembered offer to {@code playerExaminesChosenCandidate} as a value and lets the use case resolve the pick
 * and present every outcome. Any other command abandons the offer (clearing the buffer). The console makes no
 * selection decision and renders no selection outcome itself; remembering "what was just offered" is a
 * delivery-mechanism concern that stays in this driving adapter, but acting on it is the use case's.
 *
 * <p>{@link #start()} blocks until {@code bye}. It is invoked by
 * {@link com.github.gameclean.infrastructure.BootSequence} <em>after</em> the world and player have
 * been seeded. This is a plain singleton, not an {@code ApplicationRunner}: the boot order is stated
 * explicitly in {@code BootSequence}.
 *
 * <p>For each {@code look}, a <strong>fresh prototype</strong> {@link LookInputPort} is pulled from
 * the {@link ApplicationContext} — the cargo-clean reference idiom: a singleton adapter fetches the
 * prototype use case per interaction rather than holding one (which would silently defeat the scope).
 * The controller passes no actor: the acting player is ambient, resolved inside the use case, so this
 * adapter no longer needs the configured player id.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ConsoleSession {

    private final LineReader lineReader;
    private final CommandParser commandParser;
    private final AffordanceContext affordanceContext;
    private final ApplicationContext applicationContext;

    public void start() {
        printLine("Welcome to game-clean. Commands: 'look', 'look <target>' / 'examine <target>', 'move <exit>', 'now', 'bye'.");
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

            // Any command other than picking a number abandons a pending disambiguation offer: doing something
            // else means the player is no longer answering "which one?". (An ambiguous 'examine' re-arms it.)
            if (!(command instanceof SelectCommand)) {
                affordanceContext.clear();
            }

            // Exhaustive over the sealed Command set (no default). Only 'bye' ends the loop; a switch
            // break would only break the switch, so it signals through quitRequested and we break after.
            boolean quitRequested = false;
            switch (command) {
                case QuitCommand ignored -> {
                    // Quitting is a lifecycle decision from the intent alone — commit it *before* firing the
                    // (void, outcome-less) SuspendGame interaction, so the loop exit never reads as contingent
                    // on the use case's result. Unidirectional flow: we renounce its outcome the moment we call.
                    quitRequested = true;
                    leaveGame();
                }
                case LookCommand ignored -> lookAround();
                case ExamineCommand examine -> examineTarget(examine.getTarget());
                case SelectCommand select -> selectCandidate(select.getOrdinal());
                case MoveCommand move -> move(move.getExitName());
                case TimeCommand ignored -> checkTime();
                case UnknownCommand unknown -> guide(unknown.getInput());
            }
            if (quitRequested) {
                break;
            }
        }
    }

    private void lookAround() {
        // Pull a fresh prototype use case per interaction; it presents its own outcome.
        // The acting player is ambient — the use case resolves it; the controller passes nothing.
        LookInputPort lookUseCase = applicationContext.getBean(LookInputPort.class);
        lookUseCase.playerLooksAround();
    }

    private void move(String exitName) {
        // Same idiom as look: a fresh prototype use case per interaction, presenting its own outcome.
        // The acting player is ambient; only the chosen exit crosses inward, as a primitive.
        MoveInputPort moveUseCase = applicationContext.getBean(MoveInputPort.class);
        moveUseCase.playerMovesThrough(exitName);
    }

    private void examineTarget(String target) {
        // Designate the thing by description; if it is ambiguous the use case presents a menu and its presenter
        // arms the AffordanceContext, so the next bare number resolves (handled by selectCandidate).
        ExamineInputPort examineUseCase = applicationContext.getBean(ExamineInputPort.class);
        examineUseCase.playerExaminesTarget(target);
    }

    private void selectCandidate(int ordinal) {
        // The controller only detects the selection intent and delegates. It hands the use case the offer it
        // last remembered (a value — dependency rejection) plus the pick; the use case resolves it and presents
        // every outcome (nothing offered, no such option, no longer here, described). No decision or rendering
        // happens here — that would be a Clean Architecture violation.
        ExamineInputPort examineUseCase = applicationContext.getBean(ExamineInputPort.class);
        examineUseCase.playerExaminesChosenCandidate(ordinal, affordanceContext.currentOffer());
    }

    private void checkTime() {
        // Same idiom: a fresh prototype use case per interaction, presenting its own outcome.
        AskForTimeInputPort askForTimeUseCase = applicationContext.getBean(AskForTimeInputPort.class);
        askForTimeUseCase.playerChecksTheTime();
    }

    private void leaveGame() {
        // On the way out, bank this session's elapsed time into the clock (Model B "pause on quit"); the
        // use case presents the parting acknowledgement once the bank commits. Then the loop breaks.
        SuspendGameInputPort suspendGameUseCase = applicationContext.getBean(SuspendGameInputPort.class);
        suspendGameUseCase.playerLeavesTheGame();
    }

    private void guide(String input) {
        // The player typed something we couldn't map to a command. Recognizing it as unknown is this adapter's
        // job (the parser); deciding to guide the player, and which guidance to show, is the use case's and its
        // presenter's. We only hand the raw input inward and let the use case present — no message is rendered
        // here, so the controller never owns the command vocabulary or the outcome.
        GuidanceInputPort guidanceUseCase = applicationContext.getBean(GuidanceInputPort.class);
        guidanceUseCase.playerIssuesUnrecognizedCommand(input);
    }

    private void printLine(String text) {
        lineReader.getTerminal().writer().println(text);
        lineReader.getTerminal().flush();
    }
}
