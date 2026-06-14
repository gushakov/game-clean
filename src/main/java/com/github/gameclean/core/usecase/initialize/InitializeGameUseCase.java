package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Brings a fresh game into a playable starting state: constructs the authored world and places the
 * single player in it. Implementation of {@link InitializeGameInputPort}; framework-free, wired by the
 * composition root, exercised in isolation against mocked ports.
 *
 * <p><b>One interaction, one runtime presentation.</b> Constructing the world and placing the player are
 * two <em>phases</em> of a single system-actor goal, not two interactions: a player needs a scene to
 * stand in, so the world→player order is a <em>domain</em> precondition enforced <em>inside</em> this one
 * interaction rather than sequenced by a caller across the hexagon boundary. The decisive point is that
 * the phases do <em>not</em> each present. A {@code present*} call relinquishes control for good
 * (unidirectional flow), so on any execution path exactly <em>one</em> {@code present*} is reached and it
 * is the last act of the interaction — never "present, then continue." The world phase is therefore a
 * pair of non-presenting checkpoints feeding the single success outcome,
 * {@link InitializeGamePresenterOutputPort#presentGameInitialized}. "World already seeded" and "player
 * already present" are folded into that one success: the system actor's goal — a playable game — is met
 * identically whether the state was freshly written or already there.
 *
 * <p>The checkpoints run two-pass and transaction-tight. Construction and resolution run <em>outside</em>
 * any transaction: the intra-aggregate validity gate (value-object construction), then the
 * inter-aggregate rules that every exit target and the starting scene resolve to an authored scene —
 * resolved against the in-memory world being initialized, since on a first run the store is not seeded
 * yet. A <em>single</em> {@code doInTransaction} then holds both idempotency guards (seed-if-empty,
 * create-if-absent) together with their writes, so those read-then-write decisions cannot interleave with
 * a concurrent initialization. The lone success presentation is deferred to after-commit so it is never
 * reported before the data is durable, and the interaction returns immediately after registering it. The
 * initiating actor is the system at startup, so there is no security assertion. The single outermost
 * {@code catch} routes any unhandled error (notably a {@code PersistenceOperationsError}, which has
 * already rolled its transaction back) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InitializeGameUseCase implements InitializeGameInputPort {

    InitializeGamePresenterOutputPort presenter;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void systemInitializesGame(List<SceneEntry> sceneEntries, String startingSceneId) {
        try {
            // Initiating actor: the system at startup — no security assertion is required.

            // Checkpoint 1 — construct the scene aggregates (intra-aggregate validity gate).
            List<Scene> scenes;
            try {
                scenes = buildScenes(sceneEntries);
            } catch (IllegalArgumentException | NullPointerException e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 2 — inter-aggregate rule: every exit target resolves to an authored scene.
            Map<SceneId, List<Exit>> unresolvedExitsByScene = findUnresolvedExits(scenes);
            if (!unresolvedExitsByScene.isEmpty()) {
                presenter.presentErrorWhenExitTargetUnknown(unresolvedExitsByScene);
                return;
            }

            // Checkpoint 3 — construct the player value objects and aggregate (validity gate). The acting
            // player's id is ambient (pulled from playerOps); the starting scene is the authored carrier.
            PlayerId playerId;
            SceneId startingScene;
            Player player;
            try {
                playerId = new PlayerId(playerOps.currentPlayerId());
                startingScene = new SceneId(startingSceneId);
                player = Player.builder().id(playerId).currentScene(startingScene).build();
            } catch (IllegalArgumentException | NullPointerException e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 4 — inter-aggregate rule: the starting scene resolves to an authored scene.
            // Resolved against the in-memory world (like the exit check), not the store: on a first run
            // the scenes are not persisted yet, so a store lookup here would be a false negative.
            if (scenes.stream().noneMatch(scene -> scene.getId().equals(startingScene))) {
                presenter.presentStartingSceneUnknown(startingScene);
                return;
            }

            // Checkpoint 5 — one outcome, one atomic unit. A single transaction seeds the world if it is
            // still empty and creates the player if none exists yet; holding both read-then-write guards
            // in one transaction stops a concurrent initialization from double-seeding or double-creating.
            // Exactly one after-commit presentation reports the single success, and the interaction ends
            // here — nothing runs past a presentation.
            txOps.doInTransaction(false, () -> {
                if (sceneOps.worldIsEmpty()) {
                    scenes.forEach(sceneOps::saveScene);
                }
                if (playerRepositoryOps.findPlayer(playerId).isEmpty()) {
                    playerRepositoryOps.savePlayer(player);
                }
                txOps.doAfterCommit(() -> presenter.presentGameInitialized(scenes, playerId));
            });
            return;

        } catch (Exception e) {
            // Outermost checkpoint. Anything not handled above ends here — notably a
            // PersistenceOperationsError from a write, which has also rolled its transaction back before
            // propagating out of doInTransaction.
            presenter.presentError(e);
        }
    }

    private static List<Scene> buildScenes(List<SceneEntry> entries) {
        List<Scene> scenes = new ArrayList<>(entries.size());
        for (SceneEntry entry : entries) {
            scenes.add(Scene.builder()
                    .id(new SceneId(entry.getId()))
                    .name(entry.getName())
                    .shortDescription(entry.getShortDescription())
                    .fullDescription(entry.getFullDescription())
                    .exits(entry.getExits().stream()
                            .map(exit -> new Exit(exit.getName(), new SceneId(exit.getTarget())))
                            .toList())
                    .build());
        }
        return scenes;
    }

    private static Map<SceneId, List<Exit>> findUnresolvedExits(List<Scene> scenes) {
        Set<SceneId> known = scenes.stream().map(Scene::getId).collect(Collectors.toSet());
        Map<SceneId, List<Exit>> unresolved = new LinkedHashMap<>();
        for (Scene scene : scenes) {
            List<Exit> dangling = scene.getExits().stream()
                    .filter(exit -> !known.contains(exit.getTarget()))
                    .toList();
            if (!dangling.isEmpty()) {
                unresolved.put(scene.getId(), dangling);
            }
        }
        return unresolved;
    }
}
