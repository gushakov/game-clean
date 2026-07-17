package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.inventory.DropPresenterOutputPort;
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
 * Secondary (driven) adapter rendering the {@code Drop} use case's outcomes to the shared JLine console. Like
 * its {@code examine}/{@code take} siblings it composes the shared renderers — {@link OrientRenderer} for the
 * inherited orient not-founds, {@link ItemRenderer} for the item outcomes — and implements the three flat
 * presenter ports the use case's collaborators drive ({@code orient}, {@code select}, and {@code drop}'s own),
 * rather than extending a base presenter.
 *
 * <p>It differs from the take presenter on three axes: its terminal outcome is the drop confirmation
 * ({@link #presentItemDropped}); it arms the {@link AffordanceContext} with {@link SelectionKind#DROP} so a
 * subsequent bare number resumes <em>dropping</em>; and it renders the provenance-neutral select outcomes in
 * their <b>carry-flavored</b> English ("you are not carrying anything like that", "you are no longer carrying
 * that") — the same port methods the ground-sourced consumers render with "here" phrasing. The disambiguation
 * menu is ordered here once (stable by short description, then id) and the same order is both displayed and
 * remembered, so the visible menu and the latent offer cannot drift — see {@link TerminalExaminePresenter}
 * for the full rationale.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalDropPresenter
        implements OrientPlayerPresenterOutputPort, SelectTargetPresenterOutputPort<Item>, DropPresenterOutputPort {

    OrientRenderer orientRenderer;
    ItemRenderer itemRenderer;
    Console console;
    AffordanceContext affordanceContext;

    @Override
    public void presentItemDropped(Item item) {
        itemRenderer.renderItemDropped(item);
    }

    @Override
    public void presentNoSuchTarget(String target) {
        itemRenderer.renderNoSuchCarriedTarget(target);
    }

    @Override
    public void presentAmbiguousTarget(String target, List<Item> candidates) {
        // Decide the menu order once, here — the visible menu and the remembered offer are produced from it.
        List<Item> ordered = candidates.stream()
                .sorted(Comparator.comparing(Item::getShortDescription)
                        .thenComparing(item -> item.getId().getValue()))
                .toList();
        itemRenderer.renderAmbiguousTarget(target, ordered);
        // Flatten identities to tokens on this driven side; tag the offer DROP so a later bare number drops.
        affordanceContext.offer(SelectionKind.DROP,
                ordered.stream().map(item -> item.getId().getValue()).toList());
    }

    @Override
    public void presentTargetNoLongerAvailable(String idToken) {
        itemRenderer.renderItemNoLongerCarried(idToken);
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
        log.error("[Drop] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
