package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to sit down and play the mini-game on offer here ({@code play}). Carries no
 * argument (with one mini-game a bare {@code play} is unambiguous; {@code play <game>} waits for a second
 * game). Unarmed, the console maps it to {@code PlayBlackjackInputPort.playerSitsDownToPlay()}; while a
 * blackjack round is armed it <em>continues</em> that conversation instead, re-presenting the table.
 */
@Value
public class PlayCommand implements Command {
}
