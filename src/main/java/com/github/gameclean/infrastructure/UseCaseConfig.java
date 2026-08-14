package com.github.gameclean.infrastructure;

import com.github.gameclean.core.model.dice.SystemDice;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.corpse.CorpseBlueprintSourceOperationsOutputPort;
import com.github.gameclean.core.port.daytime.DayPhaseScheduleSourceOperationsOutputPort;
import com.github.gameclean.core.port.persistence.DayPhaseLogRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.npccommands.NpcCommandsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.blackjack.PlayBlackjackInputPort;
import com.github.gameclean.core.usecase.blackjack.PlayBlackjackUseCase;
import com.github.gameclean.core.usecase.clock.AnnounceTimeOfDayInputPort;
import com.github.gameclean.core.usecase.clock.AnnounceTimeOfDayUseCase;
import com.github.gameclean.core.usecase.clock.AskForTimeInputPort;
import com.github.gameclean.core.usecase.clock.AskForTimeUseCase;
import com.github.gameclean.core.usecase.clock.SuspendGameInputPort;
import com.github.gameclean.core.usecase.clock.SuspendGameUseCase;
import com.github.gameclean.core.usecase.combat.FightNpcInputPort;
import com.github.gameclean.core.usecase.combat.FightNpcUseCase;
import com.github.gameclean.core.usecase.explore.ExamineInputPort;
import com.github.gameclean.core.usecase.explore.ExamineUseCase;
import com.github.gameclean.core.usecase.explore.LookInputPort;
import com.github.gameclean.core.usecase.explore.LookUseCase;
import com.github.gameclean.core.usecase.explore.MoveInputPort;
import com.github.gameclean.core.usecase.explore.MoveUseCase;
import com.github.gameclean.core.usecase.guidance.GuidanceInputPort;
import com.github.gameclean.core.usecase.guidance.GuidanceUseCase;
import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import com.github.gameclean.core.usecase.initialize.InitializeGameUseCase;
import com.github.gameclean.core.usecase.inventory.DropInputPort;
import com.github.gameclean.core.usecase.inventory.DropUseCase;
import com.github.gameclean.core.usecase.inventory.InventoryInputPort;
import com.github.gameclean.core.usecase.inventory.InventoryUseCase;
import com.github.gameclean.core.usecase.inventory.TakeInputPort;
import com.github.gameclean.core.usecase.inventory.TakeUseCase;
import com.github.gameclean.core.usecase.npc.AnimateNpcsInputPort;
import com.github.gameclean.core.usecase.npc.AnimateNpcsUseCase;
import com.github.gameclean.core.usecase.npc.WanderInputPort;
import com.github.gameclean.core.usecase.npc.WanderUseCase;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcase;
import com.github.gameclean.core.usecase.select.SelectInventoryItemSubcase;
import com.github.gameclean.core.usecase.select.SelectSceneItemSubcase;
import com.github.gameclean.core.usecase.select.SelectSceneNpcSubcase;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.GameLifecycle;
import com.github.gameclean.infrastructure.terminal.conversation.BlackjackConversation;
import com.github.gameclean.infrastructure.terminal.conversation.Conversation;
import com.github.gameclean.infrastructure.terminal.conversation.DropConversation;
import com.github.gameclean.infrastructure.terminal.conversation.ExamineConversation;
import com.github.gameclean.infrastructure.terminal.conversation.HitConversation;
import com.github.gameclean.infrastructure.terminal.conversation.TakeConversation;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalAnimateNpcsPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalAnnounceTimeOfDayPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalAskForTimePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalDropPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalExaminePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalFightNpcPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalGuidancePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalInventoryPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalLookPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalMovePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalPlayBlackjackPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalSuspendGamePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalTakePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalWanderPresenter;
import com.github.gameclean.infrastructure.terminal.render.BlackjackRenderer;
import com.github.gameclean.infrastructure.terminal.render.CalendarRenderer;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.CurrentSceneRenderer;
import com.github.gameclean.infrastructure.terminal.render.ItemRenderer;
import com.github.gameclean.infrastructure.terminal.render.NpcRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import com.github.gameclean.infrastructure.world.LoggingInitializeGamePresenter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Composition Root — the single place where use cases are assembled from their ports. Use cases are
 * framework-free, so they carry no Spring stereotype; this configuration in the infrastructure ring does
 * all the wiring with an explicit {@code new}. Each bean is typed to the <em>input port</em> interface, so
 * callers (and the container) never see the implementation class — the dependency rule holds in both
 * directions.
 *
 * <p>Presenters (and subcases) are constructed ad-hoc with {@code new} here rather than injected as beans.
 * That is what lets a use case and the {@code orient} subcase it drives share a <em>single</em> presenter
 * instance — every outcome, whether presented by the parent or by the subcase, reaches the same presenter.
 * The trade-off is deliberate: presenters are no longer swappable by bean selection, so presenter-outcome
 * assertions live in the use-case unit tests (mocked presenter) while integration tests assert real
 * side-effects (persisted state).
 *
 * <p>Prototype scope: a use case is a subroutine for a single interaction and holds no state across
 * interactions; each lookup gets a fresh instance, with its own freshly-{@code new}ed presenter and subcase.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public InitializeGameInputPort initializeGameUseCase(
            GameSeedSourceOperationsOutputPort seedSourceOps,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps,
            NpcRepositoryOperationsOutputPort npcOps,
            GameClockRepositoryOperationsOutputPort gameClockRepositoryOps,
            DayPhaseLogRepositoryOperationsOutputPort dayPhaseLogRepositoryOps,
            TransactionOperationsOutputPort txOps) {
        return new InitializeGameUseCase(
                new LoggingInitializeGamePresenter(), seedSourceOps, playerOps, playerRepositoryOps,
                sceneOps, itemOps, npcOps, gameClockRepositoryOps, dayPhaseLogRepositoryOps,
                new SystemDice(), txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public LookInputPort lookUseCase(
            OrientRenderer orientRenderer,
            CurrentSceneRenderer sceneRenderer,
            Console console,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps,
            NpcRepositoryOperationsOutputPort npcOps) {
        TerminalLookPresenter presenter = new TerminalLookPresenter(orientRenderer, sceneRenderer, console);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        return new LookUseCase(presenter, orient, itemOps, npcOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public MoveInputPort moveUseCase(
            OrientRenderer orientRenderer,
            CurrentSceneRenderer sceneRenderer,
            Console console,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps,
            NpcRepositoryOperationsOutputPort npcOps,
            TransactionOperationsOutputPort txOps) {
        TerminalMovePresenter presenter = new TerminalMovePresenter(orientRenderer, sceneRenderer, console);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        return new MoveUseCase(presenter, playerRepositoryOps, sceneOps, itemOps, npcOps, txOps, orient);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ExamineInputPort examineUseCase(
            OrientRenderer orientRenderer,
            ItemRenderer itemRenderer,
            Console console,
            AffordanceContext affordanceContext,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps) {
        TerminalExaminePresenter presenter =
                new TerminalExaminePresenter(orientRenderer, itemRenderer, console, affordanceContext);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        SelectSceneItemSubcase select = new SelectSceneItemSubcase(presenter, itemOps);
        return new ExamineUseCase(presenter, orient, select, itemOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TakeInputPort takeUseCase(
            OrientRenderer orientRenderer,
            ItemRenderer itemRenderer,
            Console console,
            AffordanceContext affordanceContext,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps,
            TransactionOperationsOutputPort txOps) {
        // One presenter instance, shared with the orient and select subcases (as examine does), so every
        // outcome — taken, got-away, the orient not-founds, the disambiguation outcomes — reaches the same one.
        TerminalTakePresenter presenter =
                new TerminalTakePresenter(orientRenderer, itemRenderer, console, affordanceContext);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        SelectSceneItemSubcase select = new SelectSceneItemSubcase(presenter, itemOps);
        return new TakeUseCase(presenter, orient, select, itemOps, txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public DropInputPort dropUseCase(
            OrientRenderer orientRenderer,
            ItemRenderer itemRenderer,
            Console console,
            AffordanceContext affordanceContext,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps,
            TransactionOperationsOutputPort txOps) {
        // One presenter instance, shared with the orient and the inventory-sourced select subcases (as take
        // does with the scene-sourced one), so every outcome reaches the same presenter.
        TerminalDropPresenter presenter =
                new TerminalDropPresenter(orientRenderer, itemRenderer, console, affordanceContext);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        SelectInventoryItemSubcase select = new SelectInventoryItemSubcase(presenter, itemOps);
        return new DropUseCase(presenter, orient, select, itemOps, txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public FightNpcInputPort fightNpcUseCase(
            OrientRenderer orientRenderer,
            NpcRenderer npcRenderer,
            Console console,
            AffordanceContext affordanceContext,
            GameLifecycle gameLifecycle,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            NpcRepositoryOperationsOutputPort npcOps,
            ItemRepositoryOperationsOutputPort itemOps,
            CorpseBlueprintSourceOperationsOutputPort corpseBlueprintSourceOps,
            TransactionOperationsOutputPort txOps) {
        // One presenter instance, shared with the orient and the scene-sourced NPC select subcases (as take does
        // with items), so every outcome — both actors' strikes, got-away, the orient not-founds, the
        // disambiguation outcomes — reaches the same one. This bean is pulled by BOTH the console (player hits)
        // and the NPC command session (npcStrikesPlayer), each getting a fresh prototype. On a lethal
        // counterstrike the presenter announces game-over and latches the GameLifecycle. A lethal player strike
        // pulls the slain NPC's corpse blueprint (served by the YAML seed adapter) and writes the corpse + loot
        // through the item port in the same transaction as the delete. Dice is a domain collaborator (a fresh
        // SystemDice, like the ticker).
        TerminalFightNpcPresenter presenter =
                new TerminalFightNpcPresenter(orientRenderer, npcRenderer, console, affordanceContext, gameLifecycle);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        SelectSceneNpcSubcase select = new SelectSceneNpcSubcase(presenter, npcOps);
        return new FightNpcUseCase(
                presenter, orient, select, npcOps, playerRepositoryOps, playerOps, itemOps,
                corpseBlueprintSourceOps, txOps, new SystemDice());
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public PlayBlackjackInputPort playBlackjackUseCase(
            OrientRenderer orientRenderer,
            BlackjackRenderer blackjackRenderer,
            Console console,
            AffordanceContext affordanceContext,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps) {
        // One presenter instance, shared with the orient subcase (sit-down grounding), so every outcome —
        // the deals and settlements, the no-cards-here refusal, the orient not-founds — reaches the same one.
        // The presenter also owns the conversation's arming transcription: live-hand outcomes park the round
        // in the AffordanceContext (kind BLACKJACK), terminal outcomes disarm. No persistence or transaction
        // port: the round is an ephemeral value, and the deal's SystemDice is the conversation's only entropy.
        TerminalPlayBlackjackPresenter presenter =
                new TerminalPlayBlackjackPresenter(orientRenderer, blackjackRenderer, console, affordanceContext);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        return new PlayBlackjackUseCase(presenter, orient, new SystemDice());
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public InventoryInputPort inventoryUseCase(
            OrientRenderer orientRenderer,
            ItemRenderer itemRenderer,
            Console console,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            ItemRepositoryOperationsOutputPort itemOps) {
        // No subcases to share the presenter with: the use case resolves the player inline (player-only
        // grounding — no orient) and offers no disambiguation (no select, no AffordanceContext).
        TerminalInventoryPresenter presenter =
                new TerminalInventoryPresenter(orientRenderer, itemRenderer, console);
        return new InventoryUseCase(presenter, playerOps, playerRepositoryOps, itemOps);
    }

    /**
     * The selection conversations — singletons collected into {@code ConsoleSession}'s {@code List<Conversation>}
     * (the container is the resumer map). Each "dresses up" its use case as a resumable dialogue and pulls a
     * fresh prototype use case from the context per resume, so prototype scope is honoured (a captured prototype
     * would silently defeat it). Named classes, not anonymous, so the Template-Method base and the cast/ordinal
     * step stay unit-testable.
     */
    @Bean
    public Conversation examineConversation(ApplicationContext applicationContext) {
        return new ExamineConversation(applicationContext);
    }

    @Bean
    public Conversation takeConversation(ApplicationContext applicationContext) {
        return new TakeConversation(applicationContext);
    }

    @Bean
    public Conversation dropConversation(ApplicationContext applicationContext) {
        return new DropConversation(applicationContext);
    }

    @Bean
    public Conversation hitConversation(ApplicationContext applicationContext) {
        return new HitConversation(applicationContext);
    }

    /**
     * The blackjack table talk — the first conversation continued by verbs rather than a bare number, so it
     * implements {@code Conversation} directly with its own {@code continuedBy} grammar and relays the armed
     * affordance's opaque round envelope instead of tokens.
     */
    @Bean
    public Conversation blackjackConversation(ApplicationContext applicationContext) {
        return new BlackjackConversation(applicationContext);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public AskForTimeInputPort askForTimeUseCase(
            CalendarRenderer calendarRenderer,
            Console console,
            CalendarSourceOperationsOutputPort calendarSourceOps,
            GameClockRepositoryOperationsOutputPort gameClockRepositoryOps,
            GameTimeSourceOutputPort gameTimeSourceOps) {
        TerminalAskForTimePresenter presenter = new TerminalAskForTimePresenter(calendarRenderer, console);
        return new AskForTimeUseCase(presenter, calendarSourceOps, gameClockRepositoryOps, gameTimeSourceOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public AnnounceTimeOfDayInputPort announceTimeOfDayUseCase(
            Console console,
            CalendarSourceOperationsOutputPort calendarSourceOps,
            DayPhaseScheduleSourceOperationsOutputPort dayPhaseScheduleSourceOps,
            GameClockRepositoryOperationsOutputPort gameClockRepositoryOps,
            DayPhaseLogRepositoryOperationsOutputPort dayPhaseLogRepositoryOps,
            GameTimeSourceOutputPort gameTimeSourceOps,
            TransactionOperationsOutputPort txOps) {
        TerminalAnnounceTimeOfDayPresenter presenter = new TerminalAnnounceTimeOfDayPresenter(console);
        return new AnnounceTimeOfDayUseCase(presenter, calendarSourceOps, dayPhaseScheduleSourceOps,
                gameClockRepositoryOps, dayPhaseLogRepositoryOps, gameTimeSourceOps, new SystemDice(), txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public AnimateNpcsInputPort animateNpcsUseCase(
            NpcRepositoryOperationsOutputPort npcOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            NpcCommandsOutputPort npcCommandsOps) {
        // A read-only policy: it decides and dispatches commands, writing nothing — so no transaction port, and
        // its presenter needs no renderer (it only ever presents the quiet stripe, a trace log). The dispatched
        // executions narrate themselves through their own use cases (Wander, FightNpc). Dice is a domain
        // collaborator (a fresh SystemDice, like the ticker).
        TerminalAnimateNpcsPresenter presenter = new TerminalAnimateNpcsPresenter();
        return new AnimateNpcsUseCase(
                presenter, npcOps, sceneOps, playerOps, playerRepositoryOps, new SystemDice(), npcCommandsOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public WanderInputPort wanderUseCase(
            NpcRenderer npcRenderer,
            NpcRepositoryOperationsOutputPort npcOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            TransactionOperationsOutputPort txOps) {
        // The executing interaction for a dispatched wander, pulled fresh per command by the NPC command
        // session. Its presenter narrates a witnessed movement (asynchronously, above the prompt) via the
        // shared NpcRenderer; the quiet stripe is a trace log.
        TerminalWanderPresenter presenter = new TerminalWanderPresenter(npcRenderer);
        return new WanderUseCase(presenter, npcOps, sceneOps, playerOps, playerRepositoryOps, txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SuspendGameInputPort suspendGameUseCase(
            Console console,
            GameClockRepositoryOperationsOutputPort gameClockRepositoryOps,
            GameTimeSourceOutputPort gameTimeSourceOps,
            TransactionOperationsOutputPort txOps) {
        TerminalSuspendGamePresenter presenter = new TerminalSuspendGamePresenter(console);
        return new SuspendGameUseCase(presenter, gameClockRepositoryOps, gameTimeSourceOps, txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GuidanceInputPort guidanceUseCase(Console console) {
        TerminalGuidancePresenter presenter = new TerminalGuidancePresenter(console);
        return new GuidanceUseCase(presenter);
    }
}
