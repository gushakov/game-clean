package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Inspects one specific thing in the player's current scene. Implementation of {@link ExamineInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked ports.
 *
 * <p>A <b>read-only</b> use case like {@code look} — no transaction, no write. Both interactions open with the
 * shared {@link OrientPlayerSubcaseInputPort orient subcase} (resolve the acting player and their current
 * scene) and then resolve a single item, differing only in how it is designated.
 *
 * <p>{@link #playerExaminesTarget(String)} designates by description: it fetches the items on the ground,
 * asks each whether it {@link Item#matches(String) matches} the fragment (Tell-Don't-Ask), and branches on the
 * count — 0 (no such target), 1 (describe it), or N (offer the candidates to disambiguate). It does
 * <em>not</em> order or number the candidates; that is presentation, deferred to the presenter (see below).
 *
 * <p>{@link #playerExaminesItem(String)} designates by identity, completing a disambiguation: it reconstitutes
 * the {@link ItemId} (the validity gate) and re-fetches the current scene's items to confirm the chosen one is
 * <em>still there</em> — a concurrent removal becomes a "no longer here" outcome rather than a stale render.
 * Re-validating by re-reading live state (not trusting the offered list) is what makes the remembered identity
 * concurrency-honest.
 *
 * <p>On every path exactly one {@code present*} is reached: the orient subcase presents the two not-found
 * outcomes and throws {@link SubcaseAlreadyPresented} (swallowed as a no-op); the explicit branches present and
 * return; and the outermost {@code catch} routes anything unhandled to {@code presentError}. A malformed id in
 * {@code playerExaminesItem} lands there too — it originates from the terminal's own remembered offer, never a
 * player-authored value, so it is an internal fault, not an invalid-parameter outcome to show the player.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ExamineUseCase implements ExamineInputPort {

    ExaminePresenterOutputPort presenter;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;
    ItemRepositoryOperationsOutputPort itemOps;

    @Override
    public void playerExaminesTarget(String target) {
        try {
            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();

            // Designate by description: ask each item on the ground whether it matches (Tell-Don't-Ask).
            List<Item> matches = itemOps.findItemsInScene(bearings.getScene().getId()).stream()
                    .filter(item -> item.matches(target))
                    .toList();

            switch (matches.size()) {
                case 0 -> presenter.presentNoSuchTarget(target);
                case 1 -> presenter.presentItemDescription(matches.get(0));
                // Ambiguous: hand the candidates over for disambiguation. Ordering/numbering is the
                // presenter's, not ours — so the menu shown and the offer remembered are produced once.
                default -> presenter.presentAmbiguousTarget(target, matches);
            }

        } catch (SubcaseAlreadyPresented e) {
            // The orient subcase already presented its outcome (missing player or dangling scene); no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }

    @Override
    public void playerExaminesItem(String itemId) {
        try {
            // Reconstitute the id (validity gate). A malformed id is an internal fault — it came from our own
            // remembered offer, not from the player — so it propagates to the catch-all, not a parameter error.
            ItemId id = new ItemId(itemId);

            OrientPlayerResult bearings = orientPlayerSubcase.playerGetsBearings();

            // Re-validate against live state: the chosen item must still be on the ground here.
            Item chosen = itemOps.findItemsInScene(bearings.getScene().getId()).stream()
                    .filter(item -> item.getId().equals(id))
                    .findFirst()
                    .orElse(null);
            if (chosen == null) {
                presenter.presentItemNoLongerHere(id);
                return;
            }

            presenter.presentItemDescription(chosen);

        } catch (SubcaseAlreadyPresented e) {
            // The orient subcase already presented its outcome (missing player or dangling scene); no-op.
        } catch (Exception e) {
            presenter.presentError(e);
        }
    }
}
