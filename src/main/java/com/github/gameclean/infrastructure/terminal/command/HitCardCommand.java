package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player asks the dealer for a hit card ({@code hit} bare, or {@code hit me}). A pure marker —
 * meaningful only while a blackjack conversation is armed, where it continues the table talk as
 * {@code PlayBlackjackInputPort.playerAsksDealerForHitCard(round)}; unarmed it folds into the guidance nudge.
 *
 * <p>The token-shape split with combat's {@link HitCommand} is the parser's decision (parsing <em>is</em> the
 * adapter's job): {@code hit} with any other remainder designates an NPC to strike; bare {@code hit} — which
 * used to be unknown — and the idiomatic {@code hit me} are card talk.
 */
@Value
public class HitCardCommand implements Command {
}
