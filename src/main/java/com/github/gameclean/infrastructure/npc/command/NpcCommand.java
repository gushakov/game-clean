package com.github.gameclean.infrastructure.npc.command;

/**
 * A decided NPC action in transit on the command channel — the message payload the animate policy dispatches
 * and the NPC command session receives. It is deliberately an <strong>infrastructure-local</strong> type, the
 * outbound mirror of the terminal's inbound {@link com.github.gameclean.infrastructure.terminal.command.Command}
 * set: the core never owns a message type (the driven {@code NpcCommandsOutputPort}'s methods are the core-side
 * vocabulary), and the concrete record carrying the decision across the channel is delivery-mechanism, so it
 * lives here (§1's "would it survive a second adapter?" test — a queue/executor channel would carry the same
 * records; a different NPC brain would not).
 *
 * <p>Implementations are the small closed set of actions the policy can decide: {@link StrikePlayer},
 * {@link WanderThrough}. The {@code sealed} clause makes that closure explicit, so the session's
 * pattern-matching {@code switch} over a received command is <em>exhaustive</em> — adding a new action is a
 * compile error until the session handles it, rather than a silent drop. The payload carries the NPC as a raw
 * token (the dispatch adapter flattened {@code NpcId.asString()}); the receiving session hands it back inward as
 * a primitive and the executing use case reconstructs the {@code NpcId} (primitives across the boundary).
 */
public sealed interface NpcCommand permits StrikePlayer, WanderThrough {
}
