package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link ExamineUseCase} in isolation. The use case orchestrates two orthogonal
 * subcases — {@code orient} (where the player stands) and {@code select} (which item they mean) — so both are
 * mocked and the test pins the composition, not the disambiguation. Each subcase's own outcomes are covered
 * by {@code OrientPlayerSubcaseTest} / {@code SelectSceneItemSubcaseTest}; here we drive the exits the parent
 * must handle: a resolved item to reveal — two stripes, decided on the domain fact (a plain item's
 * description vs. a container's contents, fetched through the one persistence port this use case owns) — a
 * subcase that has already presented (the marker, swallowed as a no-op), and an unexpected failure routed to
 * the catch-all.
 */
@ExtendWith(MockitoExtension.class)
class ExamineUseCaseTest {

    private static final SceneId HERE = SceneId.of("scn1");

    @Mock
    private ExaminePresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<SceneId, Item> selectTargetSubcase;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;

    @InjectMocks
    private ExamineUseCase useCase;

    @Test
    void describesTheItemSelectResolvesByDescription() {
        Item dagger = item("itmRt4Xw7Kq", "A rusty dagger.");
        orientReturns("scn1");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", HERE)).thenReturn(dagger);

        useCase.playerExaminesTarget("dagger");

        verify(presenter).presentItemDescription(dagger);
        verifyNoMoreInteractions(presenter);
        // A plain item's reveal never queries containment — no over-fetch on the common path.
        verifyNoInteractions(itemOps);
    }

    @Test
    void describesTheItemSelectResolvesByChoice() {
        Item dagger = item("itmRt4Xw7Kq", "A rusty dagger.");
        orientReturns("scn1");
        when(selectTargetSubcase.playerDesignatesChosenCandidate(1, List.of("itmRt4Xw7Kq"), HERE)).thenReturn(dagger);

        useCase.playerExaminesChosenCandidate(1, List.of("itmRt4Xw7Kq"));

        verify(presenter).presentItemDescription(dagger);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(itemOps);
    }

    @Test
    void revealsAContainersContentsWhenTheResolvedItemIsAContainer() {
        Item chest = container("itmCh3StXw7", "An oak chest.");
        Item dagger = item("itmRt4Xw7Kq", "A rusty dagger.");
        orientReturns("scn1");
        when(selectTargetSubcase.playerDesignatesTarget("chest", HERE)).thenReturn(chest);
        when(itemOps.findItemsInside(chest.getId())).thenReturn(List.of(dagger));

        useCase.playerExaminesTarget("chest");

        verify(presenter).presentContainerContents(chest, List.of(dagger));
        verify(presenter, never()).presentItemDescription(any());
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void revealsAnEmptyContainerAsTheSameStripeWithNoContents() {
        Item chest = container("itmCh3StXw7", "An oak chest.");
        orientReturns("scn1");
        when(selectTargetSubcase.playerDesignatesChosenCandidate(1, List.of("itmCh3StXw7"), HERE)).thenReturn(chest);
        when(itemOps.findItemsInside(chest.getId())).thenReturn(List.of());

        useCase.playerExaminesChosenCandidate(1, List.of("itmCh3StXw7"));

        verify(presenter).presentContainerContents(chest, List.of());
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void routesAContainmentQueryFailureToTheCatchAll() {
        Item chest = container("itmCh3StXw7", "An oak chest.");
        orientReturns("scn1");
        when(selectTargetSubcase.playerDesignatesTarget("chest", HERE)).thenReturn(chest);
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(itemOps.findItemsInside(chest.getId())).thenThrow(boom);

        useCase.playerExaminesTarget("chest");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentContainerContents(any(), any());
    }

    @Test
    void presentsNothingWhenOrientAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerExaminesTarget("dagger");

        verifyNoInteractions(presenter, selectTargetSubcase);
    }

    @Test
    void presentsNothingWhenSelectAlreadyPresented() {
        orientReturns("scn1");
        when(selectTargetSubcase.playerDesignatesTarget("dagger", HERE)).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerExaminesTarget("dagger");

        verifyNoInteractions(presenter);
    }

    @Test
    void routesAnUnexpectedSubcaseFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(boom);

        useCase.playerExaminesTarget("dagger");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentItemDescription(any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private void orientReturns(String sceneId) {
        when(orientPlayerSubcase.playerGetsBearings())
                .thenReturn(new OrientPlayerResult(player("plr1", sceneId), scene(sceneId)));
    }

    private static Player player(String id, String currentScene) {
        return Player.builder().id(PlayerId.of(id)).currentScene(SceneId.of(currentScene))
                .hitPoints(HitPoints.full(30)).version(1).build();
    }

    private static Scene scene(String id) {
        return Scene.builder()
                .id(SceneId.of(id))
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of())
                .build();
    }

    private static Item item(String id, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.OnGround(SceneId.of("scn1")))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .build();
    }

    private static Item container(String id, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.OnGround(SceneId.of("scn1")))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the container.")
                .container(true)
                .build();
    }
}
