package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
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
 * Constructs and seeds the authored game world. Implementation of {@link ConstructWorldInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked ports.
 *
 * <p>The single interaction follows the methodology's checkpoint structure, two-pass and
 * transaction-tight:
 * <ol>
 *   <li><b>Build</b> every {@link Scene} aggregate from the entries — the intra-aggregate validity
 *       gate (a blank name, a malformed id, a null field is rejected here).</li>
 *   <li><b>Resolve</b> exit targets — the <em>inter-aggregate</em> rule that no entity can own:
 *       every exit must point at a scene this seed defines. Surfaced as a domain outcome, not a
 *       foreign-key violation.</li>
 *   <li><b>Seed once</b> inside one transaction — the emptiness guard and the per-scene writes
 *       share it, so the idempotency decision and its effect are atomic; presentation is deferred
 *       to after-commit so success is never reported before the data is durable.</li>
 * </ol>
 * Building and resolution run <em>outside</em> any transaction (no consistency boundary is needed
 * for pure construction). The initiating actor is the system at startup, so there is no security
 * assertion.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ConstructWorldUseCase implements ConstructWorldInputPort {

    ConstructWorldPresenterOutputPort presenter;
    SceneRepositoryOperationsOutputPort sceneOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void constructWorld(List<SceneEntry> sceneEntries) {
        try {
            // Initiating actor: the system at startup — no security assertion is required.

            // Checkpoint 1 — construct the aggregates (intra-aggregate validity gate).
            List<Scene> scenes;
            try {
                scenes = buildScenes(sceneEntries);
            } catch (IllegalArgumentException | NullPointerException e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 2 — inter-aggregate rule: every exit target resolves to a known scene.
            Map<SceneId, List<Exit>> unresolvedExitsByScene = findUnresolvedExits(scenes);
            if (!unresolvedExitsByScene.isEmpty()) {
                presenter.presentErrorWhenExitTargetUnknown(unresolvedExitsByScene);
                return;
            }

            // Checkpoint 3 — seed once, atomically. The emptiness guard lives inside the same
            // transaction as the writes so the seed-if-empty decision cannot interleave with a
            // concurrent construction; present only after the transaction commits.
            txOps.doInTransaction(false, () -> {
                if (!sceneOps.worldIsEmpty()) {
                    txOps.doAfterCommit(presenter::presentWorldAlreadyConstructed);
                    return;
                }
                scenes.forEach(sceneOps::saveScene);
                txOps.doAfterCommit(() -> presenter.presentSuccessfulWorldConstruction(scenes));
            });

        } catch (Exception e) {
            // Outermost checkpoint. Anything not handled above ends here — notably a
            // PersistenceOperationsError from worldIsEmpty/saveScene, which has also rolled the
            // transaction back before propagating out of doInTransaction.
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
