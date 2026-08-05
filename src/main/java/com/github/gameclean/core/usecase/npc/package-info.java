/**
 * Summary goal: <b>bring the world's NPCs to life</b>. NPCs are authored like items and spawned into the world
 * at initialization; this package owns their <em>autonomous</em> behaviour — the realization of the "Player and
 * NPCs act in parallel on shared state" thread.
 *
 * <p><b>The policy and the executions (issue #66 step 2).</b> Two goals divide the work along the
 * decide/execute line:
 * <ul>
 *   <li>{@link com.github.gameclean.core.usecase.npc.AnimateNpcsInputPort AnimateNpcs} — the <em>system</em>
 *       (a background metronome, the NPC-activity ticker) advances the NPCs one tick. It is a <b>read-only
 *       policy</b>: from one snapshot it <em>derives</em> each NPC's decision — a hostile, co-located NPC may
 *       strike; a non-hostile one may wander — and <em>dispatches</em> each decided action as a command through
 *       a driven port. It writes nothing.</li>
 *   <li>{@link com.github.gameclean.core.usecase.npc.WanderInputPort Wander} — the <em>executing</em>
 *       interaction for a dispatched wander: one NPC steps through the exit the policy chose, persists the new
 *       position, and narrates the movement if the player can witness it. (A dispatched strike executes through
 *       {@code FightNpc.npcStrikesPlayer}, in the combat package — the same channel, a different use case.)</li>
 * </ul>
 *
 * <p><b>"Dumb metronome, smart use case", polling — not the event spine.</b> The ticker carries no NPC
 * knowledge; it fires one blind call per interval and the policy owns the enumeration and the dice. Combat is a
 * persisted <em>stance</em> (a struck NPC is hostile and re-derived as fighting back every tick), not a one-shot
 * event: derived, idempotent behaviour polled by a loop, distinct from a discrete causal fact awaiting the
 * still-unbuilt outbox event spine. The command channel between the policy and the executing interactions is
 * synchronous on purpose — the loop is the retry mechanism, so a lost or rolled-back command costs one round.
 */
package com.github.gameclean.core.usecase.npc;
