package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link SelectSceneItemSubcase} in isolation — the presenter and the item port are
 * mocked and the subcase is exercised directly. The guarded prologue has two kinds of exit and the tests pin
 * both: a clean single resolution <em>returns</em> the item and presents nothing; every disambiguation
 * outcome <em>presents</em> and throws {@link SubcaseAlreadyPresented}. Because the subcase owns its candidate
 * fetch, the item port is stubbed here (not in the parent's test).
 *
 * <p>A deliberate assertion of the variation/extension design: on the ambiguous branch the subcase passes the
 * matches <em>unordered</em> (repository order) — ordering the menu is the presenter's job, so this asserts
 * membership, not order.
 */
@ExtendWith(MockitoExtension.class)
class SelectSceneItemSubcaseTest {

    private static final SceneId HERE = new SceneId("scn1");

    @Mock
    private SelectTargetPresenterOutputPort presenter;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;

    @InjectMocks
    private SelectSceneItemSubcase subcase;

    // --- playerDesignatesTarget (designation by description) ----------------------------------------

    @Test
    void returnsTheSingleItemAFragmentDesignates() {
        Item dagger = item("itmRt4Xw7Kq", "A rusty dagger.");
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(dagger, item("itmLm2bQ9Zx", "A brass lantern.")));

        Item resolved = subcase.playerDesignatesTarget("dagger", HERE);

        assertThat(resolved).isEqualTo(dagger);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsNoSuchTargetAndSignalsWhenAFragmentDesignatesNothing() {
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(item("itmRt4Xw7Kq", "A rusty dagger.")));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("sword", HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchTarget("sword");
    }

    @Test
    void presentsAmbiguousTargetUnorderedAndSignalsWhenAFragmentMatchesMany() {
        Item rustyDagger = item("itmRt4Xw7Kq", "A rusty dagger.");
        Item rustyKey = item("itmKey8Pp3a", "A rusty key.");
        when(itemOps.findItemsInScene(HERE))
                .thenReturn(List.of(rustyDagger, rustyKey, item("itmLm2bQ9Zx", "A brass lantern.")));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("rusty", HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        ArgumentCaptor<List<Item>> captor = captor();
        verify(presenter).presentAmbiguousTarget(eq("rusty"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(rustyDagger, rustyKey);
    }

    // --- playerDesignatesChosenCandidate (designation by choosing from the offer) -------------------

    @Test
    void returnsTheChosenCandidateWhenItIsStillPresent() {
        Item dagger = item("itmRt4Xw7Kq", "A rusty dagger.");
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(dagger));

        Item resolved = subcase.playerDesignatesChosenCandidate(1, List.of("itmRt4Xw7Kq", "itmKey8Pp3a"), HERE);

        assertThat(resolved).isEqualTo(dagger);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsItemNoLongerHereAndSignalsWhenTheChosenCandidateHasLeft() {
        // The token was offered earlier, but the item is no longer on the ground (taken / despawned).
        when(itemOps.findItemsInScene(HERE)).thenReturn(List.of(item("itmLm2bQ9Zx", "A brass lantern.")));

        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("itmRt4Xw7Kq"), HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentItemNoLongerHere(new ItemId("itmRt4Xw7Kq"));
    }

    @Test
    void presentsNoPendingSelectionAndSignalsWhenNothingWasOffered() {
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of(), HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoPendingSelection();
        verifyNoInteractions(itemOps);   // gated before any read
    }

    @Test
    void presentsNoSuchOptionAndSignalsWhenThePickIsOutOfRange() {
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(5, List.of("itmRt4Xw7Kq", "itmKey8Pp3a"), HERE))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchOption(5);
        verifyNoInteractions(itemOps);   // gated before any read
    }

    @Test
    void propagatesAMalformedChosenTokenWithoutPresenting() {
        // The token comes from our own remembered offer, so a malformed one is an internal fault — it reaches
        // the parent's catch-all, never a presented outcome. Built before provisioning, so no read happens.
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("not-an-item-id"), HERE))
                .isInstanceOf(InvalidDomainObjectError.class);

        verifyNoInteractions(presenter, itemOps);
    }

    // --- fixtures -----------------------------------------------------------------------------------

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
