package com.github.gameclean.infrastructure;

import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.explore.LookInputPort;
import com.github.gameclean.core.usecase.explore.LookPresenterOutputPort;
import com.github.gameclean.core.usecase.explore.LookUseCase;
import com.github.gameclean.core.usecase.explore.MoveInputPort;
import com.github.gameclean.core.usecase.explore.MovePresenterOutputPort;
import com.github.gameclean.core.usecase.explore.MoveUseCase;
import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import com.github.gameclean.core.usecase.initialize.InitializeGamePresenterOutputPort;
import com.github.gameclean.core.usecase.initialize.InitializeGameUseCase;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Composition Root — the single place where use cases are assembled from their ports. Use cases are
 * framework-free, so they carry no Spring stereotype; this configuration in the infrastructure ring
 * does all the wiring with an explicit {@code new}. The bean is typed to the <em>input port</em>
 * interface, so callers (and the container) never see the implementation class — the dependency rule
 * holds in both directions.
 *
 * <p>Prototype scope: a use case is a subroutine for a single interaction and holds no state across
 * interactions; each lookup gets a fresh instance. (It also keeps per-caller presenter injection
 * sound, the moment more than one presenter exists.)
 */
@Configuration
public class UseCaseConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public InitializeGameInputPort initializeGameUseCase(
            InitializeGamePresenterOutputPort presenter,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            TransactionOperationsOutputPort txOps) {
        return new InitializeGameUseCase(presenter, playerOps, playerRepositoryOps, sceneOps, txOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public LookInputPort lookUseCase(
            LookPresenterOutputPort presenter,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps) {
        return new LookUseCase(presenter, playerOps, playerRepositoryOps, sceneOps);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public MoveInputPort moveUseCase(
            MovePresenterOutputPort presenter,
            PlayerOperationsOutputPort playerOps,
            PlayerRepositoryOperationsOutputPort playerRepositoryOps,
            SceneRepositoryOperationsOutputPort sceneOps,
            TransactionOperationsOutputPort txOps) {
        return new MoveUseCase(presenter, playerOps, playerRepositoryOps, sceneOps, txOps);
    }
}
