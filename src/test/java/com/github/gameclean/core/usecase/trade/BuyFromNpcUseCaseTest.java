package com.github.gameclean.core.usecase.trade;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.dice.ScriptedDice;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.ItemTemplate;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.npc.Offer;
import com.github.gameclean.core.model.npc.StandingOffer;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.port.ware.WareTemplateSourceOperationsError;
import com.github.gameclean.core.port.ware.WareTemplateSourceOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
import com.github.gameclean.core.usecase.select.SelectTargetSubcaseInputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.github.gameclean.core.usecase.TransactionPortStubs.runTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link BuyFromNpcUseCase} in isolation — every collaborator mocked, the transaction
 * port stubbed with the <em>plain</em> {@code (boolean, Runnable)} helper (the use case deliberately uses the
 * plain overload, and the lock-propagation test pins exactly that contrast, the {@code DropUseCaseTest} way).
 *
 * <p>The tail's arithmetic is the heart of this test: coins are the currency items in the buyer's keeping
 * ordered ascending by flattened id, and the payment is the first {@code price} of them — so the tests pin
 * <em>which</em> coins the block deletes (the lowest, not any), that non-currency holdings are invisible to
 * the count, that the shortfall refusal costs no template pull, no mint and no transaction, and that the
 * delivered ware arrives <em>held by the buyer</em>. {@link StandingOffer}s are built from real fixtures; the
 * dice is a {@link ScriptedDice} so the minted ware id is exact.
 */
@ExtendWith(MockitoExtension.class)
class BuyFromNpcUseCaseTest {

    private static final SceneId HERE = SceneId.of("scn5");
    private static final PlayerId SELF = PlayerId.of("plr1");
    private static final List<String> OFFERED_TOKENS = List.of("npc2:room", "npc2:stew");

    @Mock
    private BuyFromNpcPresenterOutputPort presenter;
    @Mock
    private OrientPlayerSubcaseInputPort orientPlayerSubcase;
    @Mock
    private SelectTargetSubcaseInputPort<SceneId, StandingOffer> selectTargetSubcase;
    @Mock
    private ItemRepositoryOperationsOutputPort itemOps;
    @Mock
    private WareTemplateSourceOperationsOutputPort wareTemplateSourceOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    private ScriptedDice dice;
    private BuyFromNpcUseCase useCase;

    @BeforeEach
    void wire() {
        // Assembled by hand, not @InjectMocks: the dice is a scripted domain collaborator, not a mock.
        dice = new ScriptedDice();
        useCase = new BuyFromNpcUseCase(
                presenter, orientPlayerSubcase, selectTargetSubcase,
                itemOps, wareTemplateSourceOps, txOps, dice);
    }

    // --- the happy purchase --------------------------------------------------------------------------

    @Test
    void buysTheWareDesignatedByDescription_payingThePriceDeletingCoinsAndSavingTheWareHeld() {
        orientedAtScn5();
        StandingOffer ware = ware();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware);
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(coin("itm1"), coin("itm2")));
        when(wareTemplateSourceOps.loadWareTemplate("itm6")).thenReturn(keyTemplate());
        dice.willPick(0, 0, 0, 0, 0, 0, 0, 0);   // one id minted: itm00000000
        runTransaction(txOps);

        useCase.playerBuysTarget("room");

        // The price (2) coins are deleted…
        verify(itemOps).deleteItem(coin("itm1"));
        verify(itemOps).deleteItem(coin("itm2"));
        // …and the ware is saved straight into the buyer's keeping, carrying the template's facts…
        Item delivered = capturedSavedWare();
        assertThat(delivered.getId()).isEqualTo(ItemId.of("itm00000000"));
        assertThat(delivered.getLocation()).isEqualTo(new Location.HeldBy(SELF));
        assertThat(delivered.getOpens()).isEqualTo("guest-room");
        // …and the committed purchase is the one outcome presented.
        verify(presenter).presentWareBought(ware, delivered);
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void withMoreCoinsThanThePrice_paysTheLowestByIdAndLeavesTheRestUntouched() {
        orientedAtScn5();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware());
        // Deliberately unsorted from the port: the tail's own ordering decides the payment.
        when(itemOps.findItemsHeldBy(SELF))
                .thenReturn(List.of(coin("itm4"), coin("itm1"), coin("itm3"), coin("itm2")));
        when(wareTemplateSourceOps.loadWareTemplate("itm6")).thenReturn(keyTemplate());
        dice.willPick(0, 0, 0, 0, 0, 0, 0, 0);
        runTransaction(txOps);

        useCase.playerBuysTarget("room");

        // The two lowest ids by flattened order pay; the two highest are neither deleted nor (re-)saved.
        verify(itemOps).deleteItem(coin("itm1"));
        verify(itemOps).deleteItem(coin("itm2"));
        verify(itemOps, never()).deleteItem(coin("itm3"));
        verify(itemOps, never()).deleteItem(coin("itm4"));
        verify(itemOps).saveItem(any());   // exactly the delivered ware — the survivors ride on untouched
    }

    @Test
    void itemsHeldThatAreNotCurrencyAreNeitherCountedNorPaid() {
        orientedAtScn5();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware());
        // Three ordinary holdings beside the two coins: invisible to the count, safe from the block.
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(
                dagger("itm9"), coin("itm1"), heldRing("itm8"), coin("itm2"), heldRing("itm7")));
        when(wareTemplateSourceOps.loadWareTemplate("itm6")).thenReturn(keyTemplate());
        dice.willPick(0, 0, 0, 0, 0, 0, 0, 0);
        runTransaction(txOps);

        useCase.playerBuysTarget("room");

        verify(itemOps).deleteItem(coin("itm1"));
        verify(itemOps).deleteItem(coin("itm2"));
        verify(itemOps, never()).deleteItem(dagger("itm9"));
        verify(itemOps, never()).deleteItem(heldRing("itm7"));
        verify(itemOps, never()).deleteItem(heldRing("itm8"));
    }

    // --- the shortfall refusal -----------------------------------------------------------------------

    @Test
    void presentsTheShortfallAndTouchesNothingWhenNoCoinsAreHeld() {
        orientedAtScn5();
        StandingOffer ware = ware();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware);
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of());

        useCase.playerBuysTarget("room");

        verify(presenter).presentNotEnoughCoins(ware, 0);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(wareTemplateSourceOps, txOps);
        verify(itemOps, never()).deleteItem(any());
        verify(itemOps, never()).saveItem(any());
    }

    @Test
    void presentsTheShortfallAndTouchesNothingWhenOneCoinIsMissing() {
        orientedAtScn5();
        StandingOffer ware = ware();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware);
        // One real coin and two holdings that are not money: the count is one, the price is two.
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(coin("itm1"), dagger("itm9"), heldRing("itm8")));

        useCase.playerBuysTarget("room");

        verify(presenter).presentNotEnoughCoins(ware, 1);
        verifyNoMoreInteractions(presenter);
        verifyNoInteractions(wareTemplateSourceOps, txOps);   // no pull, no mint, no transaction
    }

    // --- faults --------------------------------------------------------------------------------------

    @Test
    void routesAWareTemplateFaultToTheCatchAllWithoutSpendingAnything() {
        orientedAtScn5();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware());
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(coin("itm1"), coin("itm2")));
        WareTemplateSourceOperationsError drift =
                new WareTemplateSourceOperationsError("ware ref 'itm6' resolves to no authored item");
        when(wareTemplateSourceOps.loadWareTemplate("itm6")).thenThrow(drift);

        useCase.playerBuysTarget("room");

        verify(presenter).presentError(drift);
        verifyNoMoreInteractions(presenter);
        // The pull failed before the block opened: no coin left the keeping, no ware was minted or saved.
        verify(itemOps, never()).deleteItem(any());
        verify(itemOps, never()).saveItem(any());
        verifyNoInteractions(txOps);
    }

    @Test
    void routesAPersistenceFaultInsideTheBlockToTheCatchAllAndNeverAnnouncesThePurchase() {
        orientedAtScn5();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware());
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(coin("itm1"), coin("itm2")));
        when(wareTemplateSourceOps.loadWareTemplate("itm6")).thenReturn(keyTemplate());
        dice.willPick(0, 0, 0, 0, 0, 0, 0, 0);
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        doThrow(boom).when(itemOps).deleteItem(any());
        runTransaction(txOps);

        useCase.playerBuysTarget("room");

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentWareBought(any(), any());
    }

    @Test
    void propagatesALockLossToTheCatchAllInsteadOfHandlingIt() {
        orientedAtScn5();
        when(selectTargetSubcase.playerDesignatesTarget("room", HERE)).thenReturn(ware());
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(coin("itm1"), coin("itm2")));
        when(wareTemplateSourceOps.loadWareTemplate("itm6")).thenReturn(keyTemplate());
        dice.willPick(0, 0, 0, 0, 0, 0, 0, 0);
        // Unreachable today (a held coin is single-writer) — precisely why buy mints no lock-loss outcome:
        // were it ever to fire, it is a wiring surprise and rides to the catch-all as a fault.
        OptimisticLockingError loss = new OptimisticLockingError("stale version");
        doThrow(loss).when(itemOps).deleteItem(any());
        runTransaction(txOps);

        useCase.playerBuysTarget("room");

        verify(presenter).presentError(loss);
        verify(presenter, never()).presentWareBought(any(), any());
    }

    // --- the subcases have already presented ----------------------------------------------------------

    @Test
    void presentsNothingWhenTheOrientSubcaseHasAlreadyPresented() {
        when(orientPlayerSubcase.playerGetsBearings()).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerBuysTarget("room");

        verifyNoInteractions(presenter, selectTargetSubcase, itemOps, wareTemplateSourceOps, txOps);
    }

    @Test
    void presentsNothingWhenTheSelectSubcaseHasAlreadyPresented() {
        orientedAtScn5();
        when(selectTargetSubcase.playerDesignatesTarget(any(), any())).thenThrow(new SubcaseAlreadyPresented());

        useCase.playerBuysTarget("room");

        verifyNoInteractions(presenter, itemOps, wareTemplateSourceOps, txOps);
    }

    // --- the by-choice designation --------------------------------------------------------------------

    @Test
    void buysTheChosenCandidateHandingOrdinalTokensAndSceneToTheSubcaseThenRunningTheSameTail() {
        orientedAtScn5();
        StandingOffer ware = ware();
        when(selectTargetSubcase.playerDesignatesChosenCandidate(1, OFFERED_TOKENS, HERE)).thenReturn(ware);
        when(itemOps.findItemsHeldBy(SELF)).thenReturn(List.of(coin("itm1"), coin("itm2")));
        when(wareTemplateSourceOps.loadWareTemplate("itm6")).thenReturn(keyTemplate());
        dice.willPick(0, 0, 0, 0, 0, 0, 0, 0);
        runTransaction(txOps);

        useCase.playerBuysChosenCandidate(1, OFFERED_TOKENS);

        verify(selectTargetSubcase).playerDesignatesChosenCandidate(1, OFFERED_TOKENS, HERE);
        verify(itemOps).deleteItem(coin("itm1"));
        verify(itemOps).deleteItem(coin("itm2"));
        Item delivered = capturedSavedWare();
        verify(presenter).presentWareBought(ware, delivered);
        verifyNoMoreInteractions(presenter);
    }

    // --- fixtures ------------------------------------------------------------------------------------

    private void orientedAtScn5() {
        Player player = Player.builder().id(SELF).currentScene(HERE)
                .hitPoints(HitPoints.full(30)).version(1).build();
        when(orientPlayerSubcase.playerGetsBearings()).thenReturn(new OrientPlayerResult(player, scn5()));
    }

    private Item capturedSavedWare() {
        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemOps).saveItem(saved.capture());
        return saved.getValue();
    }

    /** The innkeeper's room: two coins, delivering the brass key's template (ref itm6). */
    private static StandingOffer ware() {
        Npc keeper = Npc.builder()
                .id(NpcId.of("npc2"))
                .currentScene(HERE)
                .shortDescription("A stout innkeeper.")
                .fullDescription("A broad-shouldered woman in a flour-dusted apron.")
                .moveChance(new Chance(0, 1))
                .attackChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(25))
                .hostile(false)
                .offers(List.of(new Offer("room", "A room for the night.", 2, "itm6")))
                .build();
        return keeper.standingOffers().get(0);
    }

    private static ItemTemplate keyTemplate() {
        return ItemTemplate.builder()
                .shortDescription("A small brass key.")
                .fullDescription("A small brass key on a loop of frayed red cord.")
                .opens("guest-room")
                .build();
    }

    private static Item coin(String id) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.HeldBy(SELF))
                .shortDescription("A copper coin.")
                .fullDescription("A single dull copper coin.")
                .currency(true)
                .version(1)
                .build();
    }

    private static Item dagger(String id) {
        return ordinaryHolding(id, "A rusty dagger.");
    }

    private static Item heldRing(String id) {
        return ordinaryHolding(id, "A tarnished silver ring.");
    }

    private static Item ordinaryHolding(String id, String shortDescription) {
        return Item.builder()
                .id(ItemId.of(id))
                .location(new Location.HeldBy(SELF))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the holding.")
                .version(1)
                .build();
    }

    private static Scene scn5() {
        return Scene.builder()
                .id(HERE)
                .name("The Crooked Lantern")
                .shortDescription("A low-ceilinged inn.")
                .fullDescription("A hearth smoulders under a sign swaying on one chain.")
                .exits(List.of())
                .build();
    }
}
