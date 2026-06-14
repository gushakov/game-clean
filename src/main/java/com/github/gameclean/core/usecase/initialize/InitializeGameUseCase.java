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
 * Brings a fresh game into a playable starting state: constructs the authored world, then places the
 * single player in it. Implementation of {@link InitializeGameInputPort}; framework-free, wired by the
 * composition root, exercised in isolation against mocked ports.
 *
 * <p>The two steps are one system-actor goal, not two: a player needs a scene to stand in, so the
 * world→player order is a <em>domain</em> precondition. It is enforced <em>inside</em> this single
 * interaction — {@link #constructWorld(List)} then {@link #createPlayer(String)} as private
 * subfunctions — rather than sequenced by a caller across the hexagon boundary (which would assume the
 * first step's outcome it has, by the void/unidirectional contract, renounced the right to know). The
 * player is placed only once the world is usable; a world that fails to construct stops the interaction
 * before any player is created.
 *
 * <p>Each phase follows the methodology's checkpoint structure, two-pass and transaction-tight: build
 * and resolve run <em>outside</em> any transaction; the seed/create-if-empty guard and the writes share
 * one {@code doInTransaction}; presentation is deferred to after-commit so success is never reported
 * before the data is durable. The initiating actor is the system at startup, so there is no security
 * assertion. The single outermost {@code catch} routes any unhandled error (notably a
 * {@code PersistenceOperationsError}, which has already rolled its transaction back) to
 * {@code presentError}.
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
    public void initialize(List<SceneEntry> sceneEntries, String startingSceneId) {
        try {
            // Initiating actor: the system at startup — no security assertion is required.

            if (!constructWorld(sceneEntries)) {
                // The world is unusable and the failure was already presented; do not place a player in
                // a world that does not exist.
                return;
            }
            createPlayer(startingSceneId);

        } catch (Exception e) {
            // Outermost checkpoint. Anything not handled above ends here — notably a
            // PersistenceOperationsError from a write, which has also rolled its transaction back before
            // propagating out of doInTransaction.
            presenter.presentError(e);
        }
    }

    /**
     * Constructs and seeds the authored world — the first phase of the interaction.
     *
     * @return {@code true} if the world is usable afterwards — freshly seeded or already populated — so
     *         player placement may proceed; {@code false} if construction failed and the error was
     *         already presented. A persistence failure is <em>not</em> swallowed here: it propagates to
     *         {@link #initialize}'s outermost checkpoint, which also stops player placement.
     */
    private boolean constructWorld(List<SceneEntry> sceneEntries) {
        // Checkpoint 1 — construct the aggregates (intra-aggregate validity gate).
        List<Scene> scenes;
        try {
            scenes = buildScenes(sceneEntries);
        } catch (IllegalArgumentException | NullPointerException e) {
            presenter.presentInvalidParametersError(e);
            return false;
        }

        // Checkpoint 2 — inter-aggregate rule: every exit target resolves to a known scene.
        Map<SceneId, List<Exit>> unresolvedExitsByScene = findUnresolvedExits(scenes);
        if (!unresolvedExitsByScene.isEmpty()) {
            presenter.presentErrorWhenExitTargetUnknown(unresolvedExitsByScene);
            return false;
        }

        // Checkpoint 3 — seed once, atomically. The emptiness guard lives inside the same transaction as
        // the writes so the seed-if-empty decision cannot interleave with a concurrent construction;
        // present only after the transaction commits.
        txOps.doInTransaction(false, () -> {
            if (!sceneOps.worldIsEmpty()) {
                txOps.doAfterCommit(presenter::presentWorldAlreadyConstructed);
                return;
            }
            scenes.forEach(sceneOps::saveScene);
            txOps.doAfterCommit(() -> presenter.presentSuccessfulWorldConstruction(scenes));
        });
        return true;
    }

    /**
     * Creates and persists the single player at the configured starting scene — the second phase. The
     * last step of the interaction, so it only presents its own outcome.
     */
    private void createPlayer(String startingSceneId) {
        // Checkpoint 1 — construct the value objects and aggregate (validity gate). The acting player's
        // id is ambient (pulled from playerOps); the starting scene is the authored carrier.
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

        // Checkpoint 2 — inter-aggregate rule: the starting scene must resolve to a known scene. A read,
        // so it runs outside the transaction.
        if (sceneOps.findScene(startingScene).isEmpty()) {
            presenter.presentStartingSceneUnknown(startingScene);
            return;
        }

        // Checkpoint 3 — create once, atomically. The existence guard lives inside the same transaction
        // as the write so the create-if-absent decision cannot interleave with a concurrent creation;
        // present only after the transaction commits.
        txOps.doInTransaction(false, () -> {
            if (playerRepositoryOps.findPlayer(playerId).isPresent()) {
                txOps.doAfterCommit(() -> presenter.presentPlayerAlreadyExists(playerId));
                return;
            }
            playerRepositoryOps.savePlayer(player);
            txOps.doAfterCommit(() -> presenter.presentSuccessfulPlayerCreation(player));
        });
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
