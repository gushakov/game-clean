package com.github.gameclean.core.usecase.trade;

import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.ItemTemplate;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.port.ware.WareTemplateSourceOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Comparator;
import java.util.List;

/**
 * Buys a ware from an NPC in the player's current scene: the named offer's price is paid in coins from the
 * player's own keeping and the ware is minted into their hands. Implementation of {@link BuyFromNpcInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked subcases and ports.
 *
 * <p><b>The coins are ordinary held items — there is no purse.</b> The tail reads the buyer's keeping once,
 * keeps the {@link Item#isCurrency() currency} items, and orders them ascending by flattened id: the
 * deterministic <em>first {@code price}</em> of that order is the payment. Which physical coins leave the
 * keeping is arbitrary in a world with a single currency and no denominations; pinning the pick makes the
 * write set reproducible and testable. Items inside a held container are not counted — only what is carried
 * flat pays.
 *
 * <p><b>The vendor is read once, by the select subcase, and never again.</b> She is not re-read, not saved,
 * not version-checked: the coins never travel to her, so no write ever touches her row and a purchase
 * coexists with the animate policy's wandering by never colliding with it. There is likewise no repeat-sale
 * guard — an offer is a standing fact, and buying the same ware twice is simply buying twice.
 *
 * <p><b>Deliberately the plain transaction overload.</b> A held coin has exactly one writer (its holder), so
 * the lock-aware {@code doInTransactionWithResult(action, onLockDetected)} would be machinery for an
 * unreachable outcome; were a lock loss ever to occur it would be a wiring surprise and propagates to the
 * outermost {@code catch → presentError} like any fault (design-notes §5: contested→handler,
 * single-writer→propagate). The block holds the write set and nothing else: the paid coins' deletes, then the
 * delivered ware's save, commit-or-rollback as one — no half-purchase where coins vanish without the ware.
 * The ware's template is pulled — and its id minted — <em>before</em> the block opens, so a ware the seed no
 * longer honors fails like the integrity fault it is without ever spending coins.
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); the shortfall presents {@code presentNotEnoughCoins}
 * before anything is pulled; the success path presents after the block; the outermost {@code catch} routes
 * anything unhandled to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class BuyFromNpcUseCase implements BuyFromNpcInputPort {

    BuyFromNpcPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<SceneId, StandingOffer> selectTargetSubcase;
    ItemRepositoryOperationsOutputPort itemOps;
    WareTemplateSourceOperationsOutputPort wareTemplateSourceOps;
    TransactionOperationsOutputPort txOps;
    Dice dice;

    @Override
    public void playerBuysTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            StandingOffer ware = selectTargetSubcase.playerDesignatesTarget(
                    target, bearings.getScene().getId());
            payAndDeliver(bearings.getPlayer().getId(), ware);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerBuysChosenCandidate(int ordinal, List<String> offeredCandidateTokens) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            StandingOffer ware = selectTargetSubcase.playerDesignatesChosenCandidate(
                    ordinal, offeredCandidateTokens, bearings.getScene().getId());
            payAndDeliver(bearings.getPlayer().getId(), ware);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * The shared write tail, both designations converging on it: count the coins the keeping holds, refuse the
     * shortfall (a pure read — nothing pulled, minted, or written), then pull the ware's template, mint the
     * instance into the buyer's hands, and commit the payment — the {@code price} lowest coins by flattened id
     * deleted, the ware saved — in one narrow transaction. The success is presented as the statement after the
     * block, so the player is never told of a purchase that might still roll back. Plain overload on purpose:
     * a lock loss on a single-writer held coin is unreachable, so it is left to propagate as a fault rather
     * than handled as an outcome.
     */
    private void payAndDeliver(PlayerId buyer, StandingOffer ware) {
        int price = ware.getOffer().getPrice();
        List<Item> coins = itemOps.findItemsHeldBy(buyer).stream()
                .filter(Item::isCurrency)
                .sorted(Comparator.comparing(coin -> coin.getId().asString()))
                .toList();
        if (coins.size() < price) {
            presenter.presentNotEnoughCoins(ware, coins.size());
            return;
        }
        ItemTemplate template = wareTemplateSourceOps.loadWareTemplate(ware.getOffer().getItemRef());
        Item delivered = template.instanceHeldBy(ItemId.mint(dice), buyer);
        List<Item> payment = coins.subList(0, price);
        txOps.doInTransaction(false, () -> {
            payment.forEach(itemOps::deleteItem);
            itemOps.saveItem(delivered);
        });
        presenter.presentWareBought(ware, delivered);
    }
}
