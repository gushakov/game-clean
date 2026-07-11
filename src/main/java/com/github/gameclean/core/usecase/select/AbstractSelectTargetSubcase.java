package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Template-Method base of the {@code select} subcase: the provenance-independent skeleton of the
 * target-disambiguation dialogue. Both designation modalities live here in full — the match and 0/1/N
 * branch, the empty-offer precondition, the ordinal gate, the token reconstitution, and the live
 * re-provision-and-confirm — and the single point of variation is {@link #provisionCandidates(Object)}:
 * where the candidates come from, keyed by the coordinate type {@code <C>}.
 *
 * <p>Extracted at the <em>second</em> provisioner (issue #55: {@code examine} and {@code take} share the
 * scene ground; {@code drop} reads the player's keeping), exactly as the scene concrete's javadoc had
 * predicted — a one-level Template Method over genuine is-a target-selectors, the legitimate face of
 * inheritance the project's composition-over-inheritance stance carves out. The abstract base and its
 * generic coordinate are the same deferred decision, minted together now that two real coordinate shapes
 * exist (design-notes §4).
 *
 * <p>It fuses the two subcase paths per outcome branch, honouring "presentation is terminal" across the
 * whole interaction: each method <em>returns</em> the resolved item on a clean single resolution (presenting
 * nothing), or <em>presents</em> a disambiguation outcome and throws {@link SubcaseAlreadyPresented}.
 * Unexpected failures (a malformed remembered token, a persistence fault) propagate to the parent's
 * outermost {@code catch}. Provisioning is the subcase's own: it fetches through the port its concrete
 * holds, so a parent passes only the coordinate.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public abstract class AbstractSelectTargetSubcase<C> implements SelectTargetSubcaseInputPort<C> {

    SelectTargetPresenterOutputPort presenter;

    @Override
    public final Item playerDesignatesTarget(String fragment, C coordinate) {
        // Provision, then keep those the fragment designates (Tell-Don't-Ask: each item answers if it matches).
        List<Item> matches = provisionCandidates(coordinate).stream()
                .filter(item -> item.matches(fragment))
                .toList();

        switch (matches.size()) {
            case 0 -> {
                presenter.presentNoSuchTarget(fragment);
                throw new SubcaseAlreadyPresented();
            }
            case 1 -> {
                return matches.get(0);
            }
            default -> {
                // Ambiguous: hand the candidates over (unordered — numbering is the presenter's) and bail.
                presenter.presentAmbiguousTarget(fragment, matches);
                throw new SubcaseAlreadyPresented();
            }
        }
    }

    @Override
    public final Item playerDesignatesChosenCandidate(int ordinal, List<String> offeredTokens, C coordinate) {
        // Precondition, not a player outcome: the conversation dispatcher resumes a selection only when one is
        // armed, so an empty offer reaching here is a wiring fault. Throw to the parent's catch-all rather than
        // presenting — presenting "no such option" would mislabel a programming error as a player mistake.
        if (offeredTokens.isEmpty()) {
            throw new IllegalStateException(
                    "select chosen-candidate invoked with no pending offer — the dispatcher must resume only an armed conversation");
        }
        // Resolve the pick against the offer handed in by the driving adapter. An out-of-range pick is a real
        // player outcome. Gating here, before provisioning, so a bad pick costs no read.
        if (ordinal < 1 || ordinal > offeredTokens.size()) {
            presenter.presentNoSuchOption(ordinal);
            throw new SubcaseAlreadyPresented();
        }

        // Reconstitute the chosen token (validity gate). A malformed token came from our own remembered offer,
        // not the player, so it is an internal fault — it propagates to the parent's catch-all.
        ItemId chosenId = new ItemId(offeredTokens.get(ordinal - 1));

        // Re-provision live and confirm the chosen one is still available. Re-reading (not trusting the
        // handed-in token) is what keeps the remembered identity concurrency-honest.
        Item chosen = provisionCandidates(coordinate).stream()
                .filter(item -> item.getId().equals(chosenId))
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            presenter.presentItemNoLongerAvailable(chosenId);
            throw new SubcaseAlreadyPresented();
        }

        return chosen;
    }

    /**
     * The single point of variation: where this subcase's candidates come from, resolved against the given
     * coordinate — the scene whose ground to read, or the player whose keeping to read.
     */
    protected abstract List<Item> provisionCandidates(C coordinate);
}
