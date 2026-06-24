package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link ExamineUseCase} in isolation. Both interactions open with the
 * {@link OrientPlayerSubcaseInputPort orient subcase} and resolve an item through the item port, so those are
 * mocked and every outcome stripe is pinned. The orient not-found outcomes themselves are the subcase's
 * responsibility (covered by {@code OrientPlayerSubcaseTest}); here we mock the subcase's two exits — it
 * returns bearings, or it has already presented and signals {@link SubcaseAlreadyPresented}, or it fails
 * unexpectedly.
 *
 * <p>A deliberate assertion of the variation/extension design: on the ambiguous branch the use case passes the
 * matches <em>unordered</em> (repository order) — it does not sort. Ordering the menu is the presenter's job,
 * so this test asserts membership, not order.
 */
@ExtendWith(MockitoExtension.class)
class ExamineUseCaseTest {

    @Mock
    private ExaminePresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;

    @InjectMocks
    private ExamineUseCase useCase;

    // --- playerExaminesTarget (designation by description) ------------------------------------------

    @Test
    void describes_the_single_item_a_fragment_designates() {
        Item dagger = item("itm1", "A rusty dagger.");
        orientReturns("scn1");
        when(itemOps.findItemsInScene(new SceneId("scn1"))).thenReturn(List.of(dagger, item("itm2", "A brass lantern.")));

        useCase.playerExaminesTarget("dagger");

        verify(presenter).presentItemDescription(dagger);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void reports_no_such_target_when_a_fragment_designates_nothing() {
        orientReturns("scn1");
        when(itemOps.findItemsInScene(new SceneId("scn1"))).thenReturn(List.of(item("itm1", "A rusty dagger.")));

        useCase.playerExaminesTarget("sword");

        verify(presenter).presentNoSuchTarget("sword");
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void offers_the_candidates_unordered_when_a_fragment_is_ambiguous() {
        Item rustyDagger = item("itm1", "A rusty dagger.");
        Item rustyKey = item("itm2", "A rusty key.");
        orientReturns("scn1");
        when(itemOps.findItemsInScene(new SceneId("scn1")))
                .thenReturn(List.of(rustyDagger, rustyKey, item("itm3", "A brass lantern.")));

        useCase.playerExaminesTarget("rusty");

        ArgumentCaptor<List<Item>> captor = captor();
        verify(presenter).presentAmbiguousTarget(eq("rusty"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(rustyDagger, rustyKey);
        verifyNoMoreInteractions(presenter);
    }

    // --- playerExaminesChosenCandidate (designation by choosing from the offer) --------------------

    @Test
    void describes_the_chosen_candidate_when_it_is_still_present() {
        Item dagger = item("itm1", "A rusty dagger.");
        orientReturns("scn1");
        when(itemOps.findItemsInScene(new SceneId("scn1"))).thenReturn(List.of(dagger));

        // The console hands in the offer it remembered and the player's 1-based pick.
        useCase.playerExaminesChosenCandidate(1, List.of("itm1", "itm2"));

        verify(presenter).presentItemDescription(dagger);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void reports_no_longer_here_when_the_chosen_candidate_has_left_the_scene() {
        orientReturns("scn1");
        // The token was offered earlier, but the item is no longer on the ground (taken / despawned).
        when(itemOps.findItemsInScene(new SceneId("scn1"))).thenReturn(List.of(item("itm9", "A brass lantern.")));

        useCase.playerExaminesChosenCandidate(1, List.of("itm1"));

        verify(presenter).presentItemNoLongerHere(new ItemId("itm1"));
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void presents_no_pending_selection_when_nothing_was_offered() {
        // A bare number with an empty offer — the use case owns this outcome, not the controller.
        useCase.playerExaminesChosenCandidate(1, List.of());

        verify(presenter).presentNoPendingSelection();
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(orientPlayerSubcase, itemOps);
    }

    @Test
    void presents_no_such_option_when_the_pick_is_out_of_range() {
        useCase.playerExaminesChosenCandidate(5, List.of("itm1", "itm2"));

        verify(presenter).presentNoSuchOption(5);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(orientPlayerSubcase, itemOps);
    }

    @Test
    void routes_a_malformed_chosen_token_to_the_catch_all() {
        // A token comes from our own remembered offer, so a malformed one is an internal fault, not a
        // player-authored value: it must reach presentError, never an invalid-parameter outcome. The id is
        // built before orient, so the subcase is never reached.
        useCase.playerExaminesChosenCandidate(1, List.of("not-an-item-id"));

        verify(presenter).presentError(any(Exception.class));
        verifyNoInteractions(orientPlayerSubcase, itemOps);
    }

    // --- shared control-flow paths -----------------------------------------------------------------

    @Test
    void presents_nothing_when_the_subcase_has_already_presented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerExaminesTarget("dagger");

        verifyNoInteractions(presenter, itemOps);
    }

    @Test
    void routes_an_unexpected_subcase_failure_to_the_catch_all() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(boom);

        useCase.playerExaminesTarget("dagger");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentItemDescription(any());
    }

    // --- fixtures ----------------------------------------------------------------------------------

    private void orientReturns(String sceneId) {
        when(orientPlayerSubcase.playerGetsBearings())
                .thenReturn(new OrientPlayerResult(player("plr1", sceneId), scene(sceneId)));
    }

    private static Player player(String id, String currentScene) {
        return Player.builder().id(new PlayerId(id)).currentScene(new SceneId(currentScene)).build();
    }

    private static Scene scene(String id) {
        return Scene.builder()
                .id(new SceneId(id))
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of())
                .build();
    }

    private static Item item(String id, String shortDescription) {
        return Item.builder()
                .id(new ItemId(id))
                .location(new SceneId("scn1"))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Item>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
