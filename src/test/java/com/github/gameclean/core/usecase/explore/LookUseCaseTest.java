package com.github.gameclean.core.usecase.explore;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Interaction tests for {@link LookUseCase} in isolation. {@code look}'s own surface is now thin: it drives
 * the {@link OrientPlayerSubcaseInputPort orient subcase}, fetches the items lying in the resolved scene, and
 * presents the scene with them. So the subcase and the item port are mocked and the three paths through the
 * use case are pinned — the subcase returns (fetch items, present the scene), the subcase has already
 * presented and signals {@link SubcaseAlreadyPresented} (no-op), or the subcase fails unexpectedly (route to
 * the catch-all). The not-found outcomes themselves are the subcase's responsibility and are covered by
 * {@code OrientPlayerSubcaseTest}.
 */
@ExtendWith(MockitoExtension.class)
class LookUseCaseTest {

    @Mock
    private LookPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;

    @InjectMocks
    private LookUseCase useCase;

    @Test
    void presentsThePlayersCurrentSceneWithItemsOnTheGround() {
        Scene oldGate = scene("scn1");
        List<Item> itemsOnGround = List.of(item("itm1", "scn1", "A rusty dagger."));
        when(orientPlayerSubcase.playerGetsBearings())
                .thenReturn(new OrientPlayerResult(player("plr1", "scn1"), oldGate));
        when(itemOps.findItemsInScene(new SceneId("scn1"))).thenReturn(itemsOnGround);

        useCase.playerLooksAround();

        verify(presenter).presentScene(oldGate, itemsOnGround);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void presentsNothingWhenTheSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerLooksAround();

        verifyNoInteractions(presenter, itemOps);
    }

    @Test
    void routesAnUnexpectedSubcaseFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(boom);

        useCase.playerLooksAround();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentScene(any(), any());
    }

    // --- fixtures -----------------------------------------------------------------------------------

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

    private static Item item(String id, String scene, String shortDescription) {
        return Item.builder()
                .id(new ItemId(id))
                .location(new Location.OnGround(new SceneId(scene)))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .build();
    }
}
