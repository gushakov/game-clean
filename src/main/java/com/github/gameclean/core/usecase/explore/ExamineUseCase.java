package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Inspects one specific thing in the player's current scene. Implementation of {@link ExamineInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked subcases and ports.
 *
 * <p>A <b>read-only</b> use case — no transaction, no write: each interaction composes two orthogonal
 * subcases and reveals the result. The {@code orient} subcase resolves <em>where</em> the player stands; the
 * {@code select} subcase resolves <em>which</em> item they mean (it owns the candidate fetch and every
 * disambiguation outcome). The one persistence read this use case owns is the containment query — a resolved
 * <em>container</em> reveals its contents, fetched as a query against the contained items'
 * {@code Location.Inside} references (the container owns no collection).
 *
 * <p>Both interactions converge on the reveal ({@link #revealItem(Item)}): {@link #playerExaminesTarget(String)}
 * designates by description, {@link #playerExaminesChosenCandidate(int, List)} by choosing from the offer. The
 * driving adapter hands the offered tokens in as a value (dependency rejection); designation by description vs.
 * by choice is the variation/extension structure the select subcase now owns. The reveal itself is two outcome
 * stripes — {@link ExaminePresenterOutputPort#presentItemDescription} for a plain item,
 * {@link ExaminePresenterOutputPort#presentContainerContents} for a container — decided <em>here</em> on the
 * domain fact, so the presenter renders without inspecting.
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); the success path presents the reveal; the
 * outermost {@code catch} routes anything unhandled to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ExamineUseCase implements ExamineInputPort {

    ExaminePresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort<SceneId, Item> selectTargetSubcase;
    ItemRepositoryOperationsOutputPort itemOps;

    @Override
    public void playerExaminesTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Item item = selectTargetSubcase.playerDesignatesTarget(target, bearings.getScene().getId());
            revealItem(item);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerExaminesChosenCandidate(int ordinal, List<String> offeredCandidateTokens) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Item item = selectTargetSubcase.playerDesignatesChosenCandidate(
                    ordinal, offeredCandidateTokens, bearings.getScene().getId());
            revealItem(item);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    /**
     * The terminal reveal both interactions converge on: exactly one {@code present*} fires, chosen on the
     * domain fact. A container reveals its contents (the containment query); a plain item reveals its
     * description. This is a factored terminal presentation — the helper is each interaction's last act and
     * returns nothing — not the forbidden present-then-continue straddle.
     */
    private void revealItem(Item item) {
        if (item.isContainer()) {
            presenter.presentContainerContents(item, itemOps.findItemsInside(item.getId()));
        } else {
            presenter.presentItemDescription(item);
        }
    }
}
