package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Resolves which item on the <b>ground</b> the player means among the candidates in their current scene —
 * the concrete {@code select} subcase for scene-sourced targets, used by {@code examine} (and, once it lands,
 * {@code take}). Framework-free, constructed by the composition root and handed the <em>same presenter
 * instance</em> as the parent it serves.
 *
 * <p><b>The subcase owns its whole slice — provisioning included.</b> Like {@code orient} (which holds and
 * calls its own ports rather than handing a half-resolved result back), it does not take its candidates as an
 * argument — it <em>fetches</em> them through the item port it holds. Resolving the player's designation
 * <em>among the available candidates</em> includes deciding what those candidates are; pushing the fetch out
 * to the parent would fragment the shared scenario and leave the parent coupled to the item port. The parent
 * supplies only the coordinate ({@code sceneId}); the subcase reads the ground, disambiguates, and presents
 * every disambiguation outcome.
 *
 * <p><b>One concrete subcase, by emergence.</b> There is one provisioner today (the scene ground), so
 * {@link #provisionCandidates(SceneId)} is a plain private step rather than an abstract hook. When a second
 * provisioner arrives (an inventory, for {@code drop}) it is extracted onto an {@code AbstractSelectTargetSubcase}
 * as a {@code protected abstract} method and this class becomes one of its concretes — legitimate Template-Method
 * inheritance over two real instances. Minting that base for a single subclass now would be the speculation the
 * project defers.
 *
 * <p>It fuses the two subcase paths per outcome branch, honouring "presentation is terminal" across the whole
 * interaction: each method <em>returns</em> the resolved item on a clean single resolution (presenting
 * nothing), or <em>presents</em> a disambiguation outcome and throws {@link SubcaseAlreadyPresented}.
 * Unexpected failures (a malformed remembered token, a persistence fault) propagate to the parent's outermost
 * {@code catch}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SelectSceneItemSubcase implements SelectTargetSubcaseInputPort {

    SelectTargetPresenterOutputPort presenter;
    ItemRepositoryOperationsOutputPort itemOps;

    @Override
    public Item playerDesignatesTarget(String fragment, SceneId sceneId) {
        // Provision, then keep those the fragment designates (Tell-Don't-Ask: each item answers if it matches).
        List<Item> matches = provisionCandidates(sceneId).stream()
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
    public Item playerDesignatesChosenCandidate(int ordinal, List<String> offeredTokens, SceneId sceneId) {
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
        Item chosen = provisionCandidates(sceneId).stream()
                .filter(item -> item.getId().equals(chosenId))
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            presenter.presentItemNoLongerHere(chosenId);
            throw new SubcaseAlreadyPresented();
        }

        return chosen;
    }

    /**
     * The single point of variation: where this subcase's candidates come from — scene-sourced here (items on
     * the ground in the given scene). When a second provisioner arrives this becomes a {@code protected
     * abstract} hook on an {@code AbstractSelectTargetSubcase}.
     */
    private List<Item> provisionCandidates(SceneId sceneId) {
        return itemOps.findItemsInScene(sceneId);
    }
}
