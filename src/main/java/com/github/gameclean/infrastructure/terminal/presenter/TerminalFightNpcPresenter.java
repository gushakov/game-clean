package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.combat.FightNpcPresenterOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.core.usecase.select.SelectTargetPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.AffordanceContext;
import com.github.gameclean.infrastructure.terminal.AffordanceKind;
import com.github.gameclean.infrastructure.terminal.GameLifecycle;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.NpcRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Secondary (driven) adapter rendering the {@code FightNpc} use case's outcomes to the shared JLine console.
 * Like {@link TerminalTakePresenter} it composes the shared renderers — {@link OrientRenderer} for the orient
 * not-founds, {@link NpcRenderer} for the combat and NPC-select outcomes — and implements the three flat
 * presenter ports the use case's collaborators drive ({@code orient}, {@code select} bound to {@link Npc}, and
 * {@code FightNpc}'s own), rather than extending a base presenter.
 *
 * <p>It renders <em>both</em> actors' blows. The player-initiated outcomes ({@link #presentNpcStruck},
 * {@link #presentNpcSlain}, {@link #presentNpcGotAway}) are the <b>synchronous</b> response to the player's
 * {@code hit} command, so they write to the prompt directly and arm the {@link AffordanceContext} with
 * {@link AffordanceKind#HIT} so a subsequent bare number resumes striking. The NPC-initiated outcomes
 * ({@link #presentNpcStruckPlayer}, {@link #presentPlayerSlain}) are <b>asynchronous</b> — the animate policy
 * dispatched the counterstrike and a background session runs it while the player may be at the prompt — so they
 * are narrated above the live prompt, and the quiet whiff ({@link #presentNothingHappened}) is a trace log.
 *
 * <p>The slain outcome is <em>terminal</em> for the game: {@link #presentPlayerSlain} narrates the lethal blow
 * and then hands off to {@link GameLifecycle#endGame()}, which announces game-over and latches the session to
 * end. Rendering-plus-latch is the death analog of arming the {@link AffordanceContext}: the use case already
 * decided the player is slain (it chose this stripe), and the presenter only propagates that terminal outcome
 * into session state — the console owns the actual loop-break, exactly as it does for {@code bye}.
 *
 * <p>The disambiguation menu is ordered here once (stable by short description, then id) and the same order is
 * both displayed and remembered, so the visible menu and the latent offer cannot drift — exactly as take does.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalFightNpcPresenter
        implements OrientPlayerPresenterOutputPort, SelectTargetPresenterOutputPort<Npc>, FightNpcPresenterOutputPort {

    OrientRenderer orientRenderer;
    NpcRenderer npcRenderer;
    Console console;
    AffordanceContext affordanceContext;
    GameLifecycle gameLifecycle;

    @Override
    public void presentNpcStruck(Npc npc, int damage) {
        npcRenderer.renderNpcStruck(npc, damage);
    }

    @Override
    public void presentNpcSlain(Npc npc, Optional<Item> corpse) {
        npcRenderer.renderNpcSlain(npc, corpse);
    }

    @Override
    public void presentNpcGotAway(NpcId npcId) {
        npcRenderer.renderNpcGotAway(npcId);
    }

    @Override
    public void presentNpcStruckPlayer(Npc npc, int damage, Player survivor) {
        npcRenderer.renderNpcStruckPlayer(npc, damage, survivor);
    }

    @Override
    public void presentPlayerSlain(Npc npc, int damage) {
        // Terminal outcome: narrate the lethal blow, then announce game-over and latch the session to end. The
        // console reads the latch on the next keystroke and leaves the game (banking time via SuspendGame).
        npcRenderer.renderPlayerSlain(npc, damage);
        gameLifecycle.endGame();
    }

    @Override
    public void presentNothingHappened() {
        // An NPC counterstrike that did not materialize (whiff / gone / lost race): silence for the player.
        log.trace("[FightNpc] A provoked NPC's counterstrike did not land this tick");
    }

    @Override
    public void presentNoSuchTarget(String target) {
        npcRenderer.renderNoSuchTarget(target);
    }

    @Override
    public void presentAmbiguousTarget(String target, List<Npc> candidates) {
        // Decide the menu order once, here — the visible menu and the remembered offer are produced from it.
        List<Npc> ordered = candidates.stream()
                .sorted(Comparator.comparing(Npc::getShortDescription)
                        .thenComparing(npc -> npc.getId().asString()))
                .toList();
        npcRenderer.renderAmbiguousTarget(target, ordered);
        // Flatten identities to tokens on this driven side; tag the offer HIT so a later bare number strikes.
        affordanceContext.offer(AffordanceKind.HIT,
                ordered.stream().map(npc -> npc.getId().asString()).toList());
    }

    @Override
    public void presentTargetNoLongerAvailable(String idToken) {
        npcRenderer.renderTargetNoLongerAvailable(idToken);
    }

    @Override
    public void presentNoSuchOption(int ordinal) {
        npcRenderer.renderNoSuchOption(ordinal);
    }

    @Override
    public void presentPlayerNotFound(PlayerId playerId) {
        orientRenderer.renderPlayerNotFound(playerId);
    }

    @Override
    public void presentCurrentSceneNotFound(SceneId sceneId) {
        orientRenderer.renderCurrentSceneNotFound(sceneId);
    }

    @Override
    public void presentError(Exception e) {
        log.error("[FightNpc] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
