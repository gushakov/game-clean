/**
 * Summary goal: <b>bring the world's NPCs to life</b>. NPCs are authored like items and spawned into the world
 * at initialization; this package owns their <em>autonomous</em> behaviour — the first realization of the
 * "Player and NPCs act in parallel on shared state" thread.
 *
 * <p>One system goal so far. {@link com.github.gameclean.core.usecase.npc.AnimateNpcsInputPort AnimateNpcs}:
 * the <em>system</em> (a background metronome, the NPC-activity ticker) advances the NPCs one tick. Each tick
 * the use case enumerates every NPC, rolls its authored {@code moveChance}, and on a hit wanders it to a random
 * adjacent scene. Movements the player can witness — a departure from, or an arrival into, their current scene —
 * are narrated; everything else is silent.
 *
 * <p><b>"Dumb metronome, smart use case", and a polling concern — not the event spine.</b> The ticker carries no
 * NPC knowledge; it fires one blind call per interval and the use case owns the enumeration and the dice. This
 * is deliberately a <em>second polling metronome</em> (the peer of the time ticker), <em>not</em> the outbox
 * event spine: autonomous wandering is something NPCs do on their own schedule, distinct from an NPC
 * <em>reacting</em> to the player, which still awaits the event spine.
 */
package com.github.gameclean.core.usecase.npc;
