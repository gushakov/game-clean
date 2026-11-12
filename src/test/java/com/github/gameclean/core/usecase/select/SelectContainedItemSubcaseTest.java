package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
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
 * Interaction tests for {@link SelectContainedItemSubcase} in isolation — the container-sourced concrete of
 * the {@code select} Template Method. The dialogue skeleton lives on the shared base and is pinned through
 * each concrete; what is specific here is the provenance: the coordinate is a <em>set</em> of containers and
 * the candidates are the union of their contents ({@code findItemsInside}, one read per container).
 */
@ExtendWith(MockitoExtension.class)
class SelectContainedItemSubcaseTest {

    private static final ItemId CORPSE = ItemId.of("itmCrp5eWq1");
    private static final ItemId CHEST = ItemId.of("itmChs7Tz4k");

    @Mock
    private SelectTargetPresenterOutputPort<Item> presenter;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;

    @InjectMocks
    private SelectContainedItemSubcase subcase;

    // --- playerDesignatesTarget (designation by description) ----------------------------------------

    @Test
    void returnsTheSingleContentAFragmentDesignatesAcrossTheUnionOfTheContainers() {
        Item ring = inside(CHEST, "itmRng9Pp3a", "A tarnished silver ring.");
        when(itemOps.findItemsInside(CORPSE)).thenReturn(List.of(inside(CORPSE, "itmKnf2bQ9Z", "A gutting-knife.")));
        when(itemOps.findItemsInside(CHEST)).thenReturn(List.of(ring));

        Item resolved = subcase.playerDesignatesTarget("ring", List.of(CORPSE, CHEST));

        assertThat(resolved).isEqualTo(ring);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsNoSuchTargetAndSignalsWhenTheContainersHoldNothingLikeTheFragment() {
        // Also the empty-container case: an empty corpse holds nothing like anything.
        when(itemOps.findItemsInside(CORPSE)).thenReturn(List.of());

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("ring", List.of(CORPSE)))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentNoSuchTarget("ring");
    }

    @Test
    void presentsAmbiguousTargetAndSignalsWhenMatchesSpanSeveralContainers() {
        Item corpseRing = inside(CORPSE, "itmRng9Pp3a", "A tarnished silver ring.");
        Item chestRing = inside(CHEST, "itmRng4Xw7K", "A plain gold ring.");
        when(itemOps.findItemsInside(CORPSE)).thenReturn(List.of(corpseRing));
        when(itemOps.findItemsInside(CHEST)).thenReturn(List.of(chestRing, inside(CHEST, "itmKnf2bQ9Z", "A gutting-knife.")));

        assertThatThrownBy(() -> subcase.playerDesignatesTarget("ring", List.of(CORPSE, CHEST)))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        ArgumentCaptor<List<Item>> captor = captor();
        verify(presenter).presentAmbiguousTarget(eq("ring"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(corpseRing, chestRing);
    }

    // --- playerDesignatesChosenCandidate (designation by choosing from the offer) -------------------

    @Test
    void returnsTheChosenCandidateWhenItStillLiesInsideOneOfTheContainers() {
        Item ring = inside(CHEST, "itmRng9Pp3a", "A tarnished silver ring.");
        when(itemOps.findItemsInside(CORPSE)).thenReturn(List.of());
        when(itemOps.findItemsInside(CHEST)).thenReturn(List.of(ring));

        Item resolved = subcase.playerDesignatesChosenCandidate(
                2, List.of("itmRng4Xw7K", "itmRng9Pp3a"), List.of(CORPSE, CHEST));

        assertThat(resolved).isEqualTo(ring);
        verifyNoInteractions(presenter);
    }

    @Test
    void presentsTargetNoLongerAvailableAndSignalsWhenTheChosenCandidateIsNoLongerInside() {
        when(itemOps.findItemsInside(CORPSE)).thenReturn(List.of());

        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("itmRng9Pp3a"), List.of(CORPSE)))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentTargetNoLongerAvailable("itmRng9Pp3a");
    }

    @Test
    void presentsTargetNoLongerAvailableWithoutAnyReadWhenNoContainerIsInReach() {
        // An empty coordinate (every container gone) provisions nothing — the same honest miss.
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("itmRng9Pp3a"), List.of()))
                .isInstanceOf(SubcaseAlreadyPresented.class);

        verify(presenter).presentTargetNoLongerAvailable("itmRng9Pp3a");
        verifyNoInteractions(itemOps);
    }

    @Test
    void propagatesAMalformedChosenTokenWithoutPresenting() {
        assertThatThrownBy(() -> subcase.playerDesignatesChosenCandidate(1, List.of("not-an-item-id"), List.of(CORPSE)))
                .isInstanceOf(InvalidDomainObjectError.class);

        verifyNoInteractions(presenter, itemOps);
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private static Item inside(ItemId container, String id, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.Inside(container))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Item>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
