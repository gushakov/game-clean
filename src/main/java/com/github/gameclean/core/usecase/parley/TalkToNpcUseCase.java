package com.github.gameclean.core.usecase.parley;

import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Optional;

/**
 * The parley use case — the player talks to an NPC. Implementation of {@link TalkToNpcInputPort}; framework-free,
 * wired by the composition root, exercised in isolation against mocked subcases and a mocked {@link Dice}.
 *
 * <p>Pure orchestration over the two orthogonal subcases {@code hit} already composes — {@code orient} resolves
 * <em>where</em> the player stands, {@code select} (bound to an NPC-in-scene candidate) resolves <em>which</em>
 * NPC they mean — and it <b>reads only</b>: no transaction, no write, no persistence port of its own (the
 * select subcase holds the NPC port). It holds no value-object-construction checkpoint either: {@code orient}
 * hands it a valid scene and {@code select} a valid NPC.
 *
 * <p><b>The branching is the use case's; the pick is the NPC's.</b> Which of the four outcomes the player
 * sees is decided here, on domain facts, in a fixed order: a {@linkplain Npc#isHostile() hostile} NPC refuses
 * (the combat stance shuts the conversation — checked first, so hostility outranks eloquence and commerce: a
 * hostile vendor shows nothing); a peaceful NPC with {@linkplain Npc#standingOffers() wares} shows them, after
 * a line {@linkplain Npc#pickSaying(Dice) picked} with the dice when it {@linkplain Npc#hasSayings() has any},
 * and with no roll when it has none; a peaceful NPC with nothing authored has nothing to say; otherwise the NPC
 * picks a line and says it. The presenter is handed the decided stripe and never inspects the NPC to choose one.
 *
 * <p><b>Talk reveals standing facts; it keeps no state.</b> The wares are authored facts of the NPC, standing
 * whether or not anyone asks, so showing them commits to nothing: no write, no read of what the player holds
 * (talk does not know what the player can afford). The purchase that may follow is a separate interaction of
 * the trade goal, which re-validates the chosen ware against the live scene.
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); otherwise the resolved NPC's branch presents; the
 * outermost {@code catch} routes anything unhandled to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TalkToNpcUseCase implements TalkToNpcInputPort {

    TalkToNpcPresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<SceneId, Npc> selectTargetSubcase;
    Dice dice;

    @Override
    public void playerTalksToTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Npc npc = selectTargetSubcase.playerDesignatesTarget(target, bearings.getScene().getId());
            talkToResolvedNpc(npc);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerTalksToChosenCandidate(int ordinal, List<String> offeredTokens) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Npc npc = selectTargetSubcase.playerDesignatesChosenCandidate(
                    ordinal, offeredTokens, bearings.getScene().getId());
            talkToResolvedNpc(npc);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * The shared tail once the NPC is resolved: choose the stripe on the NPC's stance, offers and saying list,
     * and present it. Void and terminal — it ends in exactly one presentation on every path, so callers do
     * nothing after it. No write, so no transaction; the dice pick is pure entropy with no persistence effect.
     */
    private void talkToResolvedNpc(Npc npc) {
        if (npc.isHostile()) {
            // The combat stance shuts the conversation — checked before the wares and the sayings, so a hostile
            // NPC with lines or wares still refuses.
            presenter.presentNpcRefusesToTalk(npc);
            return;
        }
        List<StandingOffer> wares = npc.standingOffers();
        if (!wares.isEmpty()) {
            // A silent vendor still shows its wares: no line, and no dice roll for one.
            Optional<String> saying = npc.hasSayings() ? Optional.of(npc.pickSaying(dice)) : Optional.empty();
            presenter.presentNpcShowsWares(npc, saying, wares);
            return;
        }
        if (!npc.hasSayings()) {
            presenter.presentNpcHasNothingToSay(npc);
            return;
        }
        presenter.presentNpcSays(npc, npc.pickSaying(dice));
    }
}
