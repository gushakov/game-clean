package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Describes the player's current surroundings. Implementation of {@link LookInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked ports.
 *
 * <p>This is the project's first <b>read-only</b> use case, and it shows what that costs: nothing. The
 * whole interaction is a single subcase call and a presentation, with <em>no transaction at all</em> —
 * there is nothing to commit, so there is no {@code doInTransaction} and no after-commit hook.
 *
 * <p>The shared opening — resolve the ambient player and the scene they stand in — is delegated to the
 * {@link OrientPlayerSubcaseInputPort orient subcase} that {@code look} and {@code move} both reuse. On
 * success the subcase <em>returns</em> the player and their current scene, and {@code look} simply
 * presents that scene. On a missing player or a dangling current-scene reference the subcase has already
 * presented the outcome and says so by throwing {@link SubcaseAlreadyPresented}, which a dedicated
 * checkpoint swallows as a no-op — the presentation has happened, the interaction is over. Anything else
 * unexpected (a malformed configured id, a persistence fault) propagates to the outermost {@code catch}
 * and {@code presentError}. Either way exactly one {@code present*} is reached on every path.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class LookUseCase implements LookInputPort {

    LookPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;

    @Override
    public void playerLooksAround() {
        try {
            OrientPlayerResult playerInScene = orientPlayerSubcase.playerGetsBearings();
            presenter.presentScene(playerInScene.getScene());

        } catch (SubcaseAlreadyPresented e) {
            // The orient subcase already presented its outcome (missing player or dangling scene); no-op.
        } catch (Exception e) {
            // Outermost checkpoint: a malformed configured id or a PersistenceOperationsError ends here.
            presenter.presentError(e);
        }
    }
}
