package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.inventory.TakeFromPresenterOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.core.usecase.select.SelectTargetPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
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
 * Secondary (driven) adapter rendering the {@code TakeFrom} use case's outcomes to the shared JLine console.
 * The same flat composition as {@link TerminalTakePresenter} — the {@code orient} port, the {@code select}
 * port, and the use case's own — over the shared renderers.
 *
 * <p>It differs from the ground-take presenter where the provenance shows: the select outcomes read
 * container-flavored ("nothing like that in there", "no longer in there"), the success names the container,
 * and the menu arms {@link AffordanceKind#TAKE_FROM} so a subsequent bare number resumes taking <em>from the
 * containers here</em>. This presenter is also why {@code TakeFrom} is its own use case: one class can
 * implement {@code SelectTargetPresenterOutputPort<Item>} only once, so ground- and container-flavored
 * selection cannot share a presenter. The menu order is decided once here and both displayed and remembered.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalTakeFromPresenter
        implements OrientPlayerPresenterOutputPort, SelectTargetPresenterOutputPort<Item>, TakeFromPresenterOutputPort {

    OrientRenderer orientRenderer;
    ItemRenderer itemRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentNoSuchContainer(String containerTarget) {
        itemRenderer.renderNoSuchContainer(containerTarget);
    }

    @Override
    public void presentItemTakenFrom(Item item, Item container) {
        itemRenderer.renderItemTakenFrom(item, container);
    }

    @Override
    public void presentItemGotAway(ItemId itemId) {
        itemRenderer.renderItemGotAway(itemId);
    }

    @Override
    public void presentItemAnchored(Item item) {
        itemRenderer.renderItemAnchored(item);
    }

    @Override
    public void presentNoSuchTarget(String target) {
        itemRenderer.renderNoSuchContainedTarget(target);
    }

    @Override
    public void presentAmbiguousTarget(String target, List<Item> candidates) {
        // Decide the menu order once, here — the visible menu and the remembered offer are produced from it.
        List<Item> ordered = candidates.stream()
                .sorted(Comparator.comparing(Item::getShortDescription)
                        .thenComparing(item -> item.getId().asString()))
                .toList();
        itemRenderer.renderAmbiguousTarget(target, ordered);
        // Flatten identities to tokens on this driven side; tag the offer TAKE_FROM so a later bare number
        // takes the chosen content out of its container.
        affordanceContext.offer(AffordanceKind.TAKE_FROM,
                ordered.stream().map(item -> item.getId().asString()).toList());
    }

    @Override
    public void presentTargetNoLongerAvailable(String idToken) {
        itemRenderer.renderItemNoLongerContained(idToken);
    }

    @Override
    public void presentNoSuchOption(int ordinal) {
        itemRenderer.renderNoSuchOption(ordinal);
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
        log.error("[TakeFrom] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
