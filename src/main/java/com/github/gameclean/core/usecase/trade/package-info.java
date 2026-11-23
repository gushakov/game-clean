/**
 * The <b>trade</b> summary goal (Cockburn): interactions where the player <em>exchanges coins</em> with the
 * world's inhabitants — commerce as a third way of engaging them, beside fighting ({@code combat}) and
 * talking ({@code parley}). Its first user goal is {@code BuyFromNpc}
 * ({@link com.github.gameclean.core.usecase.trade.BuyFromNpcInputPort}) — naming a ware one of the scene's
 * NPCs offers, paying its price in coins from one's own keeping, and receiving the ware in one's hands.
 *
 * <p><b>Why its own goal, not an arm of {@code parley} or {@code inventory}.</b> {@code parley} is addressing
 * an NPC with words and changes nothing; {@code inventory} moves items between ground, hands and containers.
 * A purchase does neither: it destroys coins and mints a ware — a transaction with a passive counterparty —
 * so it gets the package its name screams. Talk showing the offers is the same designation opening reached
 * from the other side; it stays {@code parley}'s business.
 *
 * <p><b>The coins are ordinary held items.</b> There is no purse and no balance: the currency flag on an
 * {@link com.github.gameclean.core.model.item.Item} marks the money, the buyer's keeping is the wallet, and a
 * purchase is one narrow transaction — the paid coins deleted, the delivered ware saved — under the
 * {@link com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort transaction port}. The
 * ware itself is pulled from the authored seed at purchase time through the
 * {@link com.github.gameclean.core.port.ware.WareTemplateSourceOperationsOutputPort ware port}, valid by the
 * boot gate's provenance.
 */
package com.github.gameclean.core.usecase.trade;
