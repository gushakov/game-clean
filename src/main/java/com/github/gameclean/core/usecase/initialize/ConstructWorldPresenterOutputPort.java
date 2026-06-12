package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

import java.util.List;
import java.util.Map;

/**
 * Presenter (driven) output port for {@code ConstructWorld}, co-located with its use case because
 * the two always change together. Extends {@link ErrorHandlingPresenterOutputPort} for the
 * catch-all {@code presentError}. Every method is {@code void} and follows the
 * {@code present + Outcome + Qualifier} grammar; domain objects pass straight through (immutable, so
 * the presenter cannot corrupt them — no Response-Model DTOs).
 *
 * <p>The implementation for the system-at-startup actor presents to a <em>log file</em>, not a
 * console — this is a system interaction with no interactive audience.
 */
public interface ConstructWorldPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: the world was empty and the given scenes were constructed and seeded. */
    void presentSuccessfulWorldConstruction(List<Scene> scenes);

    /** Neutral idempotent outcome: the world was already populated, so seeding was skipped. */
    void presentWorldAlreadyConstructed();

    /**
     * Validation failure while constructing the aggregates from authored input — a blank name, a
     * malformed id, a null field: the intra-aggregate validity gate rejected an entry.
     */
    void presentInvalidParametersError(Exception e);

    /**
     * Inter-aggregate consistency failure: one or more exits point at a {@link SceneId} that no
     * authored scene defines. Reported as a meaningful domain outcome rather than a foreign-key
     * violation. Keyed by the source scene's id, each mapped to the offending exits.
     */
    void presentErrorWhenExitTargetUnknown(Map<SceneId, List<Exit>> unresolvedExitsByScene);
}
