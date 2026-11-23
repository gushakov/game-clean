package com.github.gameclean.core.usecase.trade;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Driven (output) port for the {@code BuyFromNpc} use case's own outcomes — the two the transaction itself
 * settles. The rest of the dialogue is presented through the ports the collaborators own: the orient subcase
 * reports a missing player or dangling scene through {@code OrientPlayerPresenterOutputPort}, and an unknown
 * ware, an ambiguous one and a stale menu pick go through the select subcase's
 * {@code SelectTargetPresenterOutputPort<StandingOffer>} — the same presenter instance implements all three,
 * so the player experiences one dialogue.
 *
 * <p>Both methods arrive <em>after</em> the purchase transaction has committed (or before it ever opened, for
 * the refusal): presentation never runs inside the block.
 */
public interface BuyFromNpcPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * The purchase completed: the coins are paid and the ware is in the player's hands. The presenter learns
     * the price paid from the offer and renders the ware from the delivered item — the transaction's facts,
     * never a re-read.
     *
     * @param ware      the purchased standing offer (vendor, name and price included)
     * @param delivered the freshly minted item now held by the buyer
     */
    void presentWareBought(StandingOffer ware, Item delivered);

    /**
     * The player holds fewer coins than the ware's price: nothing was pulled, minted, or written — the
     * purchase simply did not happen.
     *
     * @param ware       the ware the player tried to buy
     * @param coinsHeld  how many coins the player's keeping actually holds (below the offer's price)
     */
    void presentNotEnoughCoins(StandingOffer ware, int coinsHeld);
}
