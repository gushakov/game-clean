package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.usecase.blackjack.PlayBlackjackInputPort;
import com.github.gameclean.core.usecase.clock.AskForTimeInputPort;
import com.github.gameclean.core.usecase.clock.SuspendGameInputPort;
import com.github.gameclean.core.usecase.combat.HitInputPort;
import com.github.gameclean.core.usecase.explore.ExamineInputPort;
import com.github.gameclean.core.usecase.explore.LookInputPort;
import com.github.gameclean.core.usecase.explore.MoveInputPort;
import com.github.gameclean.core.usecase.guidance.GuidanceInputPort;
import com.github.gameclean.core.usecase.inventory.DropInputPort;
import com.github.gameclean.core.usecase.inventory.InventoryInputPort;
import com.github.gameclean.core.usecase.inventory.TakeInputPort;
import com.github.gameclean.infrastructure.terminal.command.*;
import com.github.gameclean.infrastructure.terminal.conversation.Conversation;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Primary (driving) adapter: the interactive player session — a blocking read loop on the main thread
 * that drives the application from the console. It is a thin <em>controller</em>: read a line, ask the
 * {@link CommandParser} for the player's intent, and delegate to a use case ({@code look}, {@code examine},
 * {@code move}, {@code now}) and/or control the loop ({@code bye} banks the session via the
 * {@code SuspendGame} use case and stops the loop). Even an <em>unrecognized</em> line delegates: the parser
 * decides it matched no command, and the {@code Guidance} use case decides to steer the player and presents
 * the guidance. The session-opening welcome is delegated too — the system's own first turn fires
 * {@code Guidance.systemGreetsPlayer()}. It carries no game logic and <strong>presents nothing itself</strong>:
 * every byte the player sees is a use case's presented output.
 *
 * <p><b>The loop is the internalized request-dispatcher, not an exception to fire-and-forget.</b> A web
 * controller or a telnet server gets its dispatch loop from the container; a console adapter has none, so it
 * embeds the loop here. The invariant each turn upholds: obtain exactly one unit of work, dispatch it to at
 * most one use case, then yield to the top — the only statements after a dispatch are loop control
 * ({@code continue} / {@code break}), never result-inspection, outcome-branching, or a second use-case call.
 * So fire-and-forget holds <em>per turn</em> with no exception: the loop is the mechanism that re-arms for the
 * next request. The welcome is the system's turn-1 request (dispatched directly, then {@code continue}); it is
 * deliberately <em>not</em> a {@code WelcomeCommand} in the parsed-intent {@code Command} set, because the
 * parser never produces it (design-notes §9). {@code bye} is intercepted before the dispatch switch because it
 * must {@code break} the loop, which a switch arm cannot do without a flag.
 *
 * <p>It also holds the one piece of conversational state the design admits: the armed {@link Affordance} in
 * the shared {@link AffordanceContext} resource — a disambiguation offer's tokens, or an ephemeral
 * conversation's opaque state envelope (a blackjack round) — tagged with the {@link SelectionKind} of the
 * conversation that armed it. Routing is generic over dialogues: while an affordance is armed, the matching
 * {@code Conversation} (the injected handlers are the resumer map) gets <em>first crack</em> at each parsed
 * line through its own {@code continuedBy} predicate — a bare number continues a selection, the table-talk
 * verbs continue blackjack — and on acceptance the console hands it the armed affordance whole, as a value,
 * letting the resuming use case decide and present every outcome. A refused line <em>abandons</em> the armed
 * conversation (clearing the buffer — for an ephemeral dialogue that is the forfeit) and dispatches normally;
 * a stray continuation verb or number with nothing armed folds into the guidance nudge. The console makes no
 * conversation decision and renders no outcome itself; remembering "what was just afforded" is a
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
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ConsoleSession {

    LineReader lineReader;
    CommandParser commandParser;
    AffordanceContext affordanceContext;
    ApplicationContext applicationContext;
    List<Conversation> conversations;

    /**
     * Wiring-time completeness check: every {@link SelectionKind} must have a {@link Conversation} handler, so
     * an armed offer can always be resumed. Spring collects the {@code Conversation} beans into
     * {@link #conversations}; if a kind has no handler the application fails fast at startup rather than silently
     * dropping the player's pick at runtime.
     */
    @PostConstruct
    void assertEveryKindHasAConversation() {
        Set<SelectionKind> handled = EnumSet.noneOf(SelectionKind.class);
        for (Conversation conversation : conversations) {
            handled.add(conversation.kind());
        }
        Set<SelectionKind> missing = EnumSet.allOf(SelectionKind.class);
        missing.removeAll(handled);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No Conversation handler wired for selection kinds: " + missing);
        }
    }

    public void start() {
        boolean greeted = false;
        while (true) {

            // Turn 1 is the system's own request: greet the player. One use-case call, then yield — no line is
            // read this turn. The welcome is dispatched directly, not as a parsed Command (the parser never
            // produces it), keeping the Command set exactly the parser's output (design-notes §9).
            if (!greeted) {
                greetPlayer();
                greeted = true;
                continue;
            }

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

            // 'bye' is intercepted before the dispatch switch: it fires SuspendGame (fire-and-forget) and must
            // stop the loop, which a switch arm cannot do without a flag. The break is unconditional — we
            // renounce the use case's outcome the moment we call it (unidirectional flow).
            if (command instanceof QuitCommand) {
                leaveGame();
                break;
            }

            // Armed-conversation first crack: while a dialogue is armed, its handler's own continuedBy
            // predicate decides whether this line continues it (a bare number continues a selection; the
            // table-talk verbs continue blackjack). On acceptance the armed affordance is handed over whole,
            // as a value, and the turn ends — one dispatch, then yield.
            Conversation armedConversation = conversationForArmedKind();
            if (armedConversation != null && armedConversation.continuedBy(command)) {
                armedConversation.resume(command, affordanceContext.current());
                continue;
            }

            // Anything else abandons the armed conversation: doing something different means the player is no
            // longer answering it. For a selection that just drops the menu (an ambiguous 'examine' re-arms
            // it); for an ephemeral dialogue it is the forfeit — the dealer sweeps the cards.
            affordanceContext.clear();

            // Exactly one use-case dispatch per turn; nothing runs after it but the loop re-arming. Exhaustive
            // over the sealed Command set (no default); QuitCommand is handled above, so its arm is a no-op.
            // Continuation intents reaching this switch had nothing armed to continue — stray input, folded
            // into the guidance nudge like any unrecognized line.
            switch (command) {
                case LookCommand ignored -> lookAround();
                case ExamineCommand examine -> examineTarget(examine.getTarget());
                case TakeCommand take -> takeTarget(take.getTarget());
                case DropCommand drop -> dropTarget(drop.getTarget());
                case HitCommand hit -> hitTarget(hit.getTarget());
                case InventoryCommand ignored -> reviewBelongings();
                case PlayCommand ignored -> sitDownToPlay();
                case SelectCommand select -> guide(Integer.toString(select.getOrdinal()));
                case HitCardCommand ignored -> guide("hit");
                case StandCommand ignored -> guide("stand");
                case GameStandingCommand ignored -> guide("game");
                case MoveCommand move -> move(move.getExitName());
                case TimeCommand ignored -> checkTime();
                case UnknownCommand unknown -> guide(unknown.getInput());
                case QuitCommand ignored -> { /* intercepted above — unreachable here */ }
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
        // arms the AffordanceContext (kind EXAMINE), so the next bare number resolves (handled by selectCandidate).
        ExamineInputPort examineUseCase = applicationContext.getBean(ExamineInputPort.class);
        examineUseCase.playerExaminesTarget(target);
    }

    private void takeTarget(String target) {
        // Same idiom as examine: a fresh prototype use case per interaction. An ambiguous target makes the use
        // case present a menu and arm the AffordanceContext (kind TAKE), so the next bare number resumes taking.
        TakeInputPort takeUseCase = applicationContext.getBean(TakeInputPort.class);
        takeUseCase.playerTakesTarget(target);
    }

    private void dropTarget(String target) {
        // Same idiom as take: a fresh prototype use case per interaction. An ambiguous target makes the use
        // case present a menu and arm the AffordanceContext (kind DROP), so the next bare number resumes dropping.
        DropInputPort dropUseCase = applicationContext.getBean(DropInputPort.class);
        dropUseCase.playerDropsTarget(target);
    }

    private void hitTarget(String target) {
        // Same idiom as take: a fresh prototype use case per interaction. An ambiguous target makes the use
        // case present a menu and arm the AffordanceContext (kind HIT), so the next bare number resumes striking.
        HitInputPort hitUseCase = applicationContext.getBean(HitInputPort.class);
        hitUseCase.playerHitsTarget(target);
    }

    private void reviewBelongings() {
        // Same idiom as look: a fresh prototype use case per interaction, presenting its own outcome.
        // No target and no disambiguation follow-up — nothing arms the AffordanceContext here.
        InventoryInputPort inventoryUseCase = applicationContext.getBean(InventoryInputPort.class);
        inventoryUseCase.playerReviewsBelongings();
    }

    private void sitDownToPlay() {
        // Same idiom as look: a fresh prototype use case per interaction, presenting its own outcome. On a
        // successful deal the use case's presenter arms the AffordanceContext (kind BLACKJACK) with the round,
        // so the table-talk verbs continue the hand. While a round is armed, 'play' never reaches here — the
        // blackjack conversation's first crack routes it to the anytime table view instead.
        PlayBlackjackInputPort playBlackjackUseCase = applicationContext.getBean(PlayBlackjackInputPort.class);
        playBlackjackUseCase.playerSitsDownToPlay();
    }

    /**
     * The conversation handler owning the armed affordance's kind, or {@code null} when nothing is armed. The
     * container of Conversation beans IS the resumer map — this only matches, it decides nothing; the
     * wiring-time completeness check guarantees an armed kind always finds its handler.
     */
    private Conversation conversationForArmedKind() {
        SelectionKind armed = affordanceContext.kind();
        if (armed == null) {
            return null;
        }
        return conversations.stream()
                .filter(conversation -> conversation.kind() == armed)
                .findFirst()
                .orElse(null);
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

    private void greetPlayer() {
        // The system's session-opening turn: a fresh prototype use case presents the welcome and command list.
        // The console renders nothing itself — the greeting is the use case's presented outcome, like any other.
        GuidanceInputPort guidanceUseCase = applicationContext.getBean(GuidanceInputPort.class);
        guidanceUseCase.systemGreetsPlayer();
    }
}
