package com.github.gameclean.infrastructure;

import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.id.IdGeneratorOperationsOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.randomness.RandomnessOperationsOutputPort;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.clock.AskForTimeInputPort;
import com.github.gameclean.core.usecase.clock.AskForTimeUseCase;
import com.github.gameclean.core.usecase.clock.SuspendGameInputPort;
import com.github.gameclean.core.usecase.clock.SuspendGameUseCase;
import com.github.gameclean.core.usecase.explore.LookInputPort;
import com.github.gameclean.core.usecase.explore.LookUseCase;
import com.github.gameclean.core.usecase.explore.MoveInputPort;
import com.github.gameclean.core.usecase.explore.MoveUseCase;
import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import com.github.gameclean.core.usecase.initialize.InitializeGameUseCase;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcase;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalAskForTimePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalLookPresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalMovePresenter;
import com.github.gameclean.infrastructure.terminal.presenter.TerminalSuspendGamePresenter;
import com.github.gameclean.infrastructure.terminal.render.CalendarRenderer;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.CurrentSceneRenderer;
import com.github.gameclean.infrastructure.world.LoggingInitializeGamePresenter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
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
            GameClockRepositoryOperationsOutputPort gameClockRepositoryOps,
            IdGeneratorOperationsOutputPort idGeneratorOps,
            RandomnessOperationsOutputPort randomnessOps,
            TransactionOperationsOutputPort txOps) {
        return new InitializeGameUseCase(
                new LoggingInitializeGamePresenter(), seedSourceOps, playerOps, playerRepositoryOps,
                sceneOps, itemOps, gameClockRepositoryOps, idGeneratorOps, randomnessOps, txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public LookInputPort lookUseCase(
            CurrentSceneRenderer sceneRenderer,
            Console console,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps) {
        TerminalLookPresenter presenter = new TerminalLookPresenter(sceneRenderer, console);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        return new LookUseCase(presenter, orient, itemOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public MoveInputPort moveUseCase(
            CurrentSceneRenderer sceneRenderer,
            Console console,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            ItemRepositoryOperationsOutputPort itemOps,
            TransactionOperationsOutputPort txOps) {
        TerminalMovePresenter presenter = new TerminalMovePresenter(sceneRenderer, console);
        OrientPlayerSubcase orient = new OrientPlayerSubcase(presenter, playerOps, playerRepositoryOps, sceneOps);
        return new MoveUseCase(presenter, playerRepositoryOps, sceneOps, itemOps, txOps, orient);
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
    public SuspendGameInputPort suspendGameUseCase(
            Console console,
            GameClockRepositoryOperationsOutputPort gameClockRepositoryOps,
            GameTimeSourceOutputPort gameTimeSourceOps,
            TransactionOperationsOutputPort txOps) {
        TerminalSuspendGamePresenter presenter = new TerminalSuspendGamePresenter(console);
        return new SuspendGameUseCase(presenter, gameClockRepositoryOps, gameTimeSourceOps, txOps);
    }
}
