package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.inventory.TakePresenterOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.core.usecase.select.SelectTargetPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.SelectionKind;
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
 * Secondary (driven) adapter rendering the {@code Take} use case's outcomes to the shared JLine console. Like
 * {@link TerminalExaminePresenter} it composes the shared renderers — {@link OrientRenderer} for the inherited
 * orient not-founds, {@link ItemRenderer} for the item outcomes — and implements the three flat presenter ports
 * the use case's collaborators drive ({@code orient}, {@code select}, and {@code take}'s own), rather than
 * extending a base presenter.
 *
 * <p>It differs from the examine presenter on exactly two axes: its terminal outcomes are <em>take</em>
 * outcomes ({@link #presentItemTaken}, {@link #presentItemGotAway}) rather than the item description, and it
 * arms the {@link AffordanceContext} with {@link SelectionKind#TAKE} so a subsequent bare number resumes
 * <em>taking</em>. The disambiguation menu is ordered here once (stable by short description, then id) and the
 * same order is both displayed and remembered, so the visible menu and the latent offer cannot drift — exactly
 * as examine does it (see {@link TerminalExaminePresenter} for the full rationale).
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalTakePresenter
        implements OrientPlayerPresenterOutputPort, SelectTargetPresenterOutputPort, TakePresenterOutputPort {

    OrientRenderer orientRenderer;
    ItemRenderer itemRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentItemTaken(Item item) {
        itemRenderer.renderItemTaken(item);
    }

    @Override
    public void presentItemGotAway(ItemId itemId) {
        itemRenderer.renderItemGotAway(itemId);
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
        itemRenderer.renderAmbiguousTarget(target, ordered);
        // Flatten identities to tokens on this driven side; tag the offer TAKE so a later bare number takes.
        affordanceContext.offer(SelectionKind.TAKE,
                ordered.stream().map(item -> item.getId().getValue()).toList());
    }

    @Override
    public void presentItemNoLongerHere(ItemId itemId) {
        itemRenderer.renderItemNoLongerHere(itemId);
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
        log.error("[Take] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
