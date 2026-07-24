package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.npccommands.NpcCommandsOutputPort;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Advances the world's NPCs one tick of autonomous activity. Implementation of {@link AnimateNpcsInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked ports. The initiating
 * actor is the <em>system</em> (the background NPC-activity ticker fires it on a fixed real-time interval), so
 * there is no security assertion — the peer of {@code AnnounceTimeOfDay}.
 *
 * <p><b>A read-only policy — decide, then dispatch, never execute.</b> This use case performs no writes, holds
 * no transaction, and registers no {@code doAfterCommit}. It is the Event-Storming <em>policy</em>: from a
 * single snapshot read it <em>derives</em> each NPC's decision, then <em>dispatches</em> each decided action as
 * a command through {@link NpcCommandsOutputPort}. A driving adapter turns each command back into the
 * <em>executing</em> interaction ({@code FightNpc.npcStrikesPlayer} for a strike, {@code Wander.npcWandersThrough}
 * for a wander), where the writes and their transactions live. The tick itself never touches an aggregate's
 * state, so there is no version contention here.
 *
 * <p><b>Combat is a persisted stance, wander is the default (issue #66 step 2).</b> Each NPC's decision derives
 * from its persisted state, re-derived every tick:
 * <ul>
 *   <li><b>Hostile</b> NPCs never wander — the stance pins them in the fight. Co-located with the player and the
 *       authored {@code attackChance} rolls a hit → dispatch a <b>strike</b>; otherwise (roll fails, or not
 *       co-located, or no player) <em>stand ground</em> (no command).</li>
 *   <li><b>Non-hostile</b> NPCs roll their {@code moveChance}; on a hit the policy picks a random exit of the
 *       NPC's current scene and dispatches a <b>wander</b> carrying that exit (the executing interaction never
 *       re-rolls); a dead-end or unresolvable scene means no command.</li>
 * </ul>
 * The policy owns <em>all</em> the dice — the attack gate and the exit pick — so the command carries only the
 * decided detail.
 *
 * <p><b>Batch, then dispatch.</b> Every decision is derived from the one snapshot <em>before</em> any command is
 * dispatched — execution is never interleaved into the decision loop — so a dispatched execution (which reads
 * and writes aggregates) can never perturb a later NPC's still-pending decision this tick. TOCTOU staleness is
 * handled where it belongs: each executing interaction re-validates its preconditions at execution time (the
 * player may have moved between decision and blow).
 *
 * <p><b>One quiet stripe every tick.</b> The policy's own outcome is always {@code presentNothingHappened} —
 * its executions narrate their own outcomes mid-run, as their own interactions, so anything the policy presented
 * would read out of order. The dice rolls and reads are pure/side-effect-free; the outermost {@code catch}
 * routes any unhandled fault (a {@code PersistenceOperationsError} on a read, a failed dispatch, an unexpected
 * bug) to {@code presentError}. A lost or rolled-back command costs one round — the next tick re-derives it.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AnimateNpcsUseCase implements AnimateNpcsInputPort {

    AnimateNpcsPresenterOutputPort presenter;
    NpcRepositoryOperationsOutputPort npcOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    Dice dice;
    NpcCommandsOutputPort commandOps;

    @Override
    public void systemAdvancesNpcs() {
        try {
            // Initiating actor: the system (the NPC ticker) — no security assertion is required.

            // One snapshot: every living NPC. An empty world is the safe pre-initialization no-op (the ticker
            // may fire before seeding): present the quiet outcome and return, resolving nothing else.
            List<Npc> npcs = npcOps.findAllNpcs();
            if (npcs.isEmpty()) {
                presenter.presentNothingHappened();
                return;
            }

            // Where the player stands, for the hostile NPCs' co-location decision (Optional, tolerant of no
            // player — a tick with an unresolvable player simply decides no strikes; the others still wander).
            SceneId playerScene = playerRepositoryOps
                    .findPlayer(PlayerId.of(playerOps.currentPlayerId()))
                    .map(Player::getCurrentScene)
                    .orElse(null);

            // Derive ALL decisions from the snapshot first (batch), touching no aggregate state.
            List<NpcCommandDecision> decisions = new ArrayList<>();
            for (Npc npc : npcs) {
                decide(npc, playerScene).ifPresent(decisions::add);
            }

            // Then dispatch every decided command, in decision order — the loop is the retry mechanism, so a
            // failed dispatch or a rolled-back execution simply costs this round.
            for (NpcCommandDecision decision : decisions) {
                switch (decision.getKind()) {
                    case STRIKE -> commandOps.dispatchStrike(decision.getNpc().getId());
                    case WANDER -> commandOps.dispatchWander(decision.getNpc().getId(), decision.getExitName());
                }
            }

            // The policy's own outcome is always quiet — the executions narrate themselves mid-run.
            presenter.presentNothingHappened();

        } catch (Exception e) {
            // Outermost checkpoint: a read fault, a failed dispatch, or an unexpected bug ends here.
            presenter.presentError(e);
        }
    }

    /**
     * Derives one NPC's decision from its persisted stance and the player's location, owning every die the
     * decision needs. A hostile NPC only ever strikes (co-located + the attack gate passes) or stands ground; a
     * non-hostile NPC only ever wanders (its move gate passes and its scene has an exit) or stays. Returns empty
     * for "no command this tick" (stand ground / stay / dead-end).
     */
    private Optional<NpcCommandDecision> decide(Npc npc, SceneId playerScene) {
        if (npc.isHostile()) {
            // Hostile NPCs never wander — the stance pins them in the fight.
            boolean coLocated = playerScene != null && npc.getCurrentScene().equals(playerScene);
            if (coLocated && dice.roll(npc.getAttackChance())) {
                return Optional.of(NpcCommandDecision.strike(npc));
            }
            return Optional.empty();   // stand ground: roll failed, not co-located, or no player
        }
        // Non-hostile: maybe wander. Roll first, then resolve the scene and pick an exit (the policy owns both).
        if (!dice.roll(npc.getMoveChance())) {
            return Optional.empty();   // stays put
        }
        Optional<Scene> from = sceneOps.findScene(npc.getCurrentScene());
        if (from.isEmpty() || from.get().getExits().isEmpty()) {
            return Optional.empty();   // unresolvable scene or a dead-end: no wander
        }
        Exit chosen = dice.pick(from.get().getExits());
        return Optional.of(NpcCommandDecision.wander(npc, chosen.getName()));
    }

    /**
     * Use-case-private record of one decided NPC command: the NPC it is for, whether to strike or wander, and —
     * for a wander — the exit the policy chose. Not a domain concept (a command is delivery-mechanism), so it
     * stays out of the model; it only carries the decision from the batch loop to the dispatch loop, keeping
     * "derive all, then dispatch all" visible in one method.
     */
    @Value
    private static class NpcCommandDecision {

        enum Kind { STRIKE, WANDER }

        Kind kind;
        Npc npc;
        String exitName;   // null for STRIKE

        static NpcCommandDecision strike(Npc npc) {
            return new NpcCommandDecision(Kind.STRIKE, npc, null);
        }

        static NpcCommandDecision wander(Npc npc, String exitName) {
            return new NpcCommandDecision(Kind.WANDER, npc, exitName);
        }
    }
}
