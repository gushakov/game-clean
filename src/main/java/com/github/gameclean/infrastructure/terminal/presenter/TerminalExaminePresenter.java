package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.explore.ExaminePresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.ItemRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code Examine} use case's outcomes to the shared JLine console.
 * The item-specific outcomes delegate to {@link ItemRenderer}; the inherited orient not-found outcomes delegate
 * to {@link OrientRenderer} (the same collaborator {@code look}/{@code move} use). It composes renderers rather
 * than extending a base presenter — the same composition stance the rest of the terminal takes.
 *
 * <p><b>The disambiguation outcome is where this presenter does more than render — and why the sorting lives
 * here, not in the use case.</b> Presenting an ambiguous match has two faces of one affordance: the
 * <em>visible</em> numbered menu, and the <em>latent</em> number→identity mapping that makes the player's next
 * "2" resolve. Both must agree on the order, so the order is decided in exactly one place — and that place is
 * the presenter, for two reasons:
 * <ul>
 *   <li><b>Ordering is a presentation concern.</b> Which candidate is "1." and which is "2." is a property of
 *       the rendered menu, not of the domain. The use case's outcome is "these things are ambiguous" (a set);
 *       imposing a display sequence on that set is rendering. Sorting in the use case would push a
 *       delivery-mechanism detail (that this UI numbers a list) into the core — the same leak we keep the whole
 *       affordance out of the core to avoid.</li>
 *   <li><b>Single source of order ⇒ the two faces cannot drift.</b> The presenter sorts once, hands the ordered
 *       list to the renderer to display (numbered 1..N), and offers the <em>same</em> ordered identities to the
 *       {@link AffordanceContext}. If the use case sorted and the presenter re-derived numbering, two places
 *       would have to agree forever; one place cannot disagree with itself.</li>
 * </ul>
 * The order is stable by short description, then by id — so repeated offers of the same ground number
 * identically, and look-alikes (instances sharing a description) get a deterministic position.
 *
 * <p>Per the methodology, the presenter stays humble: the use case already decided <em>that</em> the match was
 * ambiguous (by calling {@code presentAmbiguousTarget} rather than {@code presentItemDescription}); the
 * presenter only renders that outcome and records the numbering it produced.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalExaminePresenter implements ExaminePresenterOutputPort {

    OrientRenderer orientRenderer;
    ItemRenderer itemRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentItemDescription(Item item) {
        itemRenderer.renderItemDescription(item);
    }

    @Override
    public void presentNoSuchTarget(String target) {
        itemRenderer.renderNoSuchTarget(target);
    }

    @Override
    public void presentAmbiguousTarget(String target, List<Item> candidates) {
        // Decide the menu order once, here — the visible menu and the remembered offer are produced from it.
        List<Item> ordered = candidates.stream()
                .sorted(Comparator.comparing(Item::getShortDescription)
                        .thenComparing(item -> item.getId().getValue()))
                .toList();
        itemRenderer.renderAmbiguousTarget(target, ordered);                                  // the visible face
        // Flatten the identities to raw tokens here, on the driven side where the model legitimately lives, so
        // the buffer (read by the primary console adapter) stays model-free.
        affordanceContext.offer(ordered.stream().map(item -> item.getId().getValue()).toList());  // the latent face
    }

    @Override
    public void presentItemNoLongerHere(ItemId itemId) {
        itemRenderer.renderItemNoLongerHere(itemId);
    }

    @Override
    public void presentPlayerNotFound(PlayerId playerId) {
        orientRenderer.renderPlayerNotFound(playerId);
    }

    @Override
    public void presentCurrentSceneNotFound(SceneId sceneId) {
        orientRenderer.renderCurrentSceneNotFound(sceneId);
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Examine] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
