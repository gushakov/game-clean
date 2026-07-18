package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player asks where the mini-game stands ({@code game} / {@code table}). A pure marker —
 * meaningful only while a blackjack conversation is armed, where it continues the table talk as
 * {@code PlayBlackjackInputPort.playerExaminesGame(round)} (the Cockburn <em>anytime</em> extension);
 * unarmed it folds into the guidance nudge.
 */
@Value
public class GameStandingCommand implements Command {
}
