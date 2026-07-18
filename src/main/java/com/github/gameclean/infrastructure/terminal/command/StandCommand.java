package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player stands on their blackjack hand ({@code stand} / {@code stay}). A pure marker —
 * meaningful only while a blackjack conversation is armed, where it continues the table talk as
 * {@code PlayBlackjackInputPort.playerRequestsToStand(round)}; unarmed it folds into the guidance nudge.
 */
@Value
public class StandCommand implements Command {
}
