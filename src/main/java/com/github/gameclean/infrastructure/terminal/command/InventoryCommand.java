package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to review what they are carrying ({@code inventory} / {@code i}). Carries no
 * argument, so it is a pure marker the console maps to {@code InventoryInputPort.playerReviewsBelongings()}.
 */
@Value
public class InventoryCommand implements Command {
}
