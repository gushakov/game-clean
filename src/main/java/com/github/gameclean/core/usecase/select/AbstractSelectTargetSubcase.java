package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.designation.Designatable;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Template-Method base of the {@code select} subcase: the provenance- and candidate-independent skeleton of
 * the target-disambiguation dialogue. Both designation modalities live here in full — the match and 0/1/N
 * branch, the empty-offer precondition, the ordinal gate, the token shape gate, and the live
 * re-provision-and-confirm — and the points of variation are exactly the two facts a concrete binds:
 * {@link #provisionCandidates(Object)} (where the candidates come from, keyed by the coordinate type
 * {@code <C>}) and {@link #requireWellFormedToken(String)} (what shape the candidate type's id tokens take,
 * keyed by {@code <T>}).
 *
 * <p>Extracted at the <em>second</em> provisioner (issue #55: {@code examine} and {@code take} share the
 * scene ground; {@code drop} reads the player's keeping), exactly as the scene concrete's javadoc had
 * predicted — a one-level Template Method over genuine is-a target-selectors, the legitimate face of
 * inheritance the project's composition-over-inheritance stance carves out. Generalized in the candidate
 * type at the second candidate <em>kind</em> (issue #67: combat targets, #66): the skeleton is type-blind,
 * asking each candidate the two {@link Designatable} facts — "does this fragment designate you?" and "is
 * this token your id?" — Tell-Don't-Ask, exactly as it already asked {@code matches}. The same
 * one-instance discipline, cashed one axis over (design-notes §4).
 *
 * <p>It fuses the two subcase paths per outcome branch, honouring "presentation is terminal" across the
 * whole interaction: each method <em>returns</em> the resolved candidate on a clean single resolution
 * (presenting nothing), or <em>presents</em> a disambiguation outcome and throws
 * {@link SubcaseAlreadyPresented}. Unexpected failures (a malformed remembered token, a persistence fault)
 * propagate to the parent's outermost {@code catch}. Provisioning is the subcase's own: it fetches through
 * the port its concrete holds, so a parent passes only the coordinate.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public abstract class AbstractSelectTargetSubcase<C, T extends Designatable>
        implements SelectTargetSubcaseInputPort<C, T> {

    SelectTargetPresenterOutputPort<T> presenter;

    @Override
    public final T playerDesignatesTarget(String fragment, C coordinate) {
        // Provision, then keep those the fragment designates (Tell-Don't-Ask: each candidate answers if it matches).
        List<T> matches = provisionCandidates(coordinate).stream()
                .filter(candidate -> candidate.matches(fragment))
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
    public final T playerDesignatesChosenCandidate(int ordinal, List<String> offeredTokens, C coordinate) {
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

        // Shape gate (validity): a malformed token came from our own remembered offer, not the player, so it
        // is an internal fault — the concrete reconstitutes its candidate type's id, throwing to the parent's
        // catch-all before any read.
        String chosenToken = offeredTokens.get(ordinal - 1);
        requireWellFormedToken(chosenToken);

        // Re-provision live and confirm the chosen one is still available. Re-reading (not trusting the
        // handed-in token) is what keeps the remembered identity concurrency-honest.
        T chosen = provisionCandidates(coordinate).stream()
                .filter(candidate -> candidate.hasIdToken(chosenToken))
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            presenter.presentTargetNoLongerAvailable(chosenToken);
            throw new SubcaseAlreadyPresented();
        }

        return chosen;
    }

    /**
     * Point of variation #1 — provenance: where this subcase's candidates come from, resolved against the
     * given coordinate — the scene whose ground to read, or the player whose keeping to read.
     */
    protected abstract List<T> provisionCandidates(C coordinate);

    /**
     * Point of variation #2 — token shape: asserts the remembered id token is well-formed for this subcase's
     * candidate type, throwing (an internal fault reaching the parent's catch-all) when it is not. The
     * skeleton is type-blind, so it cannot reconstitute an id itself; the concrete runs its candidate's own
     * construction gate. Fired eagerly, before any read, so a malformed token never costs a provisioning and
     * — with an empty ground — is never mislabelled as the presented "no longer available" outcome.
     */
    protected abstract void requireWellFormedToken(String idToken);
}
