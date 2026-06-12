package com.github.gameclean.infrastructure;

import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.initialize.ConstructWorldInputPort;
import com.github.gameclean.core.usecase.initialize.ConstructWorldPresenterOutputPort;
import com.github.gameclean.core.usecase.initialize.ConstructWorldUseCase;
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
    public ConstructWorldInputPort constructWorldUseCase(
            ConstructWorldPresenterOutputPort presenter,
            SceneRepositoryOperationsOutputPort sceneOps,
            TransactionOperationsOutputPort txOps) {
        return new ConstructWorldUseCase(presenter, sceneOps, txOps);
    }
}
