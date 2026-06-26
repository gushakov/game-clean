package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Inspects one specific thing in the player's current scene. Implementation of {@link ExamineInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked subcases.
 *
 * <p>A <b>read-only</b> use case — no transaction, no write — and now <b>pure orchestration</b>: each
 * interaction composes two orthogonal subcases and presents the result. The {@code orient} subcase resolves
 * <em>where</em> the player stands; the {@code select} subcase resolves <em>which</em> item they mean (it owns
 * the candidate fetch and every disambiguation outcome). This use case holds no persistence port of its own —
 * it orients, hands the scene coordinate to select, and describes whatever single item comes back.
 *
 * <p>Both interactions converge on the one outcome this use case owns,
 * {@link ExaminePresenterOutputPort#presentItemDescription}: {@link #playerExaminesTarget(String)} designates
 * by description, {@link #playerExaminesChosenCandidate(int, List)} by choosing from the offer. The driving
 * adapter hands the offered tokens in as a value (dependency rejection); designation by description vs. by
 * choice is the variation/extension structure the select subcase now owns.
 *
 * <p>On every path exactly one {@code present*} is reached: a subcase presents its own outcome and throws
 * {@link SubcaseAlreadyPresented} (swallowed here as a no-op); the success path presents the description; the
 * outermost {@code catch} routes anything unhandled to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ExamineUseCase implements ExamineInputPort {

    ExaminePresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    SelectTargetSubcaseInputPort selectTargetSubcase;

    @Override
    public void playerExaminesTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();
            Item item = selectTargetSubcase.playerDesignatesTarget(target, bearings.getScene().getId());
            presenter.presentItemDescription(item);
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
            presenter.presentItemDescription(item);
        } catch (SubcaseAlreadyPresented e) {
            // The orient or select subcase already presented its outcome; no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }
}
