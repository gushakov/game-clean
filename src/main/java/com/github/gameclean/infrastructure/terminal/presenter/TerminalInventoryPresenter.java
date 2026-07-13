package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.usecase.inventory.InventoryPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.ItemRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code Inventory} use case's outcomes to the shared JLine
 * console. The slimmest of the item-facing presenters: one flat port, no subcase ports (the use case
 * resolves the player inline rather than opening with {@code orient}, and offers no disambiguation), and
 * so nothing to arm on the {@code AffordanceContext}.
 *
 * <p>It still composes the shared renderers rather than owning any English of its own: the player-not-found
 * rendering is {@link OrientRenderer}'s — inventory declares that outcome on its <em>own</em> port (it does
 * not open with the orient subcase) but the message is written one way across the codebase (composition,
 * the shared-rendering axis of design-notes §4).
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalInventoryPresenter implements InventoryPresenterOutputPort {

    OrientRenderer orientRenderer;
    ItemRenderer itemRenderer;
    Console console;

    @Override
    public void presentCarriedItems(List<Item> items) {
        itemRenderer.renderCarriedItems(items);
    }

    @Override
    public void presentPlayerNotFound(PlayerId playerId) {
        orientRenderer.renderPlayerNotFound(playerId);
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Inventory] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
