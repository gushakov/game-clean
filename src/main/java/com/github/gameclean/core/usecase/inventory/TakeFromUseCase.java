package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Takes an item out of a container lying in the player's current scene into the player's keeping.
 * Implementation of {@link TakeFromInputPort}; framework-free, wired by the composition root, exercised in
 * isolation against mocked subcases and ports.
 *
 * <p><b>Two designations, one dialogue.</b> The command names an item and a container, but only the item is
 * ever disambiguated. The container fragment is resolved <em>here</em>, inline, as a plain filter over the
 * scene's ground — the containers it designates — with a single outcome of its own (none:
 * {@code presentNoSuchContainer}). The surviving set is the <em>coordinate</em> handed to the {@code select}
 * subcase, which matches the item fragment over the union of their contents and owns every disambiguation
 * outcome. A second, container-level menu would buy nothing the player wants — two look-alike corpses are not
 * a choice worth asking about when only one of them holds the ring — and would need two armed menus where the
 * terminal holds one.
 *
 * <p><b>The menu pick is re-validated against reachability, not against the original fragment.</b> The
 * chosen-content interaction is handed only the offered tokens, so it re-provisions over <em>every</em>
 * container on the ground here: the remembered token identifies the item, and the live question is merely
 * "does it still lie inside something within reach?".
 *
 * <p><b>The write tail</b> mirrors {@code Take}'s. Its opening checkpoint is the anchored refusal — read off
 * the <em>item being taken</em>, never the container: a corpse is anchored and is exactly what one takes from.
 * Past that gate the item moves into the player's keeping ({@code item.takenBy} — the mutator is
 * location-agnostic, so no model change was needed) and is saved inside one narrow read-write transaction,
 * success presented only after commit. Only the contained item's row is written; the container owns no
 * collection, so it is neither written nor version-checked.
 *
 * <p><b>Concurrency.</b> Contents of a container on the ground are within any actor's reach, so the write is
 * contested like a ground take: the {@code (action, onLockDetected)} overload, a lost race presented as
 * {@code presentItemGotAway}. The handler presents, so the {@code doInTransaction} is the interaction's
 * terminal act.
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); this use case presents no-such-container, the
 * anchored refusal, the success after commit, or the lost race; the outermost {@code catch} routes anything
 * unhandled to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TakeFromUseCase implements TakeFromInputPort {

    TakeFromPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<List<ItemId>, Item> selectTargetSubcase;
    ItemRepositoryOperationsOutputPort itemOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void playerTakesTargetFrom(String itemTarget, String containerTarget) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();

            // Checkpoint — where to look: the containers on the ground here that the fragment designates.
            // None is a presented outcome of this use case's own; several is not an ambiguity to resolve —
            // all of them are searched.
            List<Item> containers = containersOnGround(bearings.getScene().getId()).stream()
                    .filter(container -> container.matches(containerTarget))
                    .toList();
            if (containers.isEmpty()) {
                presenter.presentNoSuchContainer(containerTarget);
                return;
            }

            Item item = selectTargetSubcase.playerDesignatesTarget(itemTarget, idsOf(containers));
            takeResolvedItem(bearings.getPlayer().getId(), item, containers);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerTakesChosenContent(int ordinal, List<String> offeredCandidateTokens) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();

            // No fragment travels with a menu pick: re-validate against everything within reach. If the
            // containers are gone the subcase finds nothing and presents "no longer available" itself.
            List<Item> containers = containersOnGround(bearings.getScene().getId());

            Item item = selectTargetSubcase.playerDesignatesChosenCandidate(
                    ordinal, offeredCandidateTokens, idsOf(containers));
            takeResolvedItem(bearings.getPlayer().getId(), item, containers);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * The shared write tail: refuse an anchored item, otherwise move the resolved item into the holder's
     * keeping and persist it in one narrow transaction, presenting success after commit and a lost concurrent
     * race via {@code onLockDetected}. Void and terminal — it ends in a presentation on every path, so
     * callers do nothing after it.
     */
    private void takeResolvedItem(PlayerId holder, Item item, List<Item> containers) {
        // Checkpoint — the anchored gate reads the item taken, not the container it lies in.
        if (item.isAnchored()) {
            presenter.presentItemAnchored(item);
            return;
        }
        Item container = containerHolding(item, containers);
        Item taken = item.takenBy(holder);
        txOps.doInTransaction(
                () -> {
                    itemOps.saveItem(taken);
                    txOps.doAfterCommit(() -> presenter.presentItemTakenFrom(taken, container));
                },
                () -> presenter.presentItemGotAway(item.getId()));
    }

    /** The items on the scene's ground that can hold things. Held containers are out of reach of this goal. */
    private List<Item> containersOnGround(SceneId scene) {
        return itemOps.findItemsInScene(scene).stream()
                .filter(Item::isContainer)
                .toList();
    }

    private static List<ItemId> idsOf(List<Item> containers) {
        return containers.stream().map(Item::getId).toList();
    }

    /**
     * The container, among those searched, that the resolved item lies inside — needed only to name it in the
     * success outcome. The item was provisioned from exactly these containers, so a miss is a wiring fault
     * (a select subcase bound to a different provenance), not a player outcome: it throws to the catch-all,
     * like the select skeleton's own empty-offer precondition.
     */
    private static Item containerHolding(Item item, List<Item> containers) {
        return containers.stream()
                .filter(container -> new Location.Inside(container.getId()).equals(item.getLocation()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "take-from resolved an item lying in none of the searched containers — "
                                + "the select subcase must provision from the containers it is handed"));
    }
}
