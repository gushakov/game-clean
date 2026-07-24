package com.github.gameclean.core.port.npccommands;

import com.github.gameclean.core.model.npc.NpcId;

/**
 * Driven (output) port through which the animate <em>policy</em> dispatches the actions it has decided for the
 * NPCs — the core-side vocabulary of the NPC command channel. The {@code OutputPort} suffix marks the hexagonal
 * direction: the read-only policy ({@code AnimateNpcs}) is the caller; an infrastructure adapter builds a
 * message and puts it on a channel, and a driving adapter turns it back into the <em>executing</em> interaction
 * ({@code FightNpc.npcStrikesPlayer} / {@code Wander.npcWandersThrough}).
 *
 * <p><b>Commands, not events — and the methods are the vocabulary.</b> The message crosses <em>after</em> the
 * decision is made: one addressee, imperative, exactly one execution per decision. So the vocabulary is a small
 * set of imperative methods here, not a message type the core owns — the outbound twin of the presenter-port
 * convention (the driven port's methods are the core-side vocabulary; the concrete message record is
 * infrastructure, §8/§9). An event abstraction ({@code NpcDecidedToRetaliate}) would lie twice: it invites
 * zero-to-many consumers (wrong — exactly one swing per decision) and tempts a handler to re-decide (leaking the
 * policy out of the core).
 *
 * <p><b>The channel is the sanctioned bridge between interactions.</b> A use case never calls another use case
 * (that would make it a controller-orchestrator); one actor's decided action reaches another interaction only by
 * going out through this port and back in through a driving adapter. The dispatch is synchronous and
 * fire-and-forget from the policy's view: a failure to dispatch or execute costs one round (the polling loop
 * re-derives the decision from persisted stance next tick — the loop is the retry mechanism), so no result and
 * no error type crosses back.
 */
public interface NpcCommandsOutputPort {

    /**
     * Dispatches the decided <b>strike</b>: the given (hostile, co-located) NPC swings at the player. Turned by
     * the receiving driving adapter into {@code FightNpc.npcStrikesPlayer(npcId)}, which re-validates and lands
     * (or whiffs) the blow.
     *
     * @param npcId the striking NPC
     */
    void dispatchStrike(NpcId npcId);

    /**
     * Dispatches the decided <b>wander</b>: the given (non-hostile) NPC steps through the named exit. The exit is
     * chosen by the policy (which owns all the dice) and carried in the command, so the executing interaction
     * never re-rolls. Turned by the receiving driving adapter into {@code Wander.npcWandersThrough(npcId, exit)}.
     *
     * @param npcId    the wandering NPC
     * @param exitName the name of the exit the policy chose for it to leave by
     */
    void dispatchWander(NpcId npcId, String exitName);
}
