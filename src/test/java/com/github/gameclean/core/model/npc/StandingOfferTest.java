package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StandingOffer} — the vendor-and-offer pair a {@code buy} transaction designates: the
 * always-valid construction gate (neither half may be missing), the designation facts that speak of the
 * <em>ware</em> alone (matching never reaches into the vendor's description or the item handle), the composite
 * {@code vendorId:offerRef} token — its format, its re-confirmation, and the shared shape gate a select
 * concrete fires before any read. Pinning these directly here (rather than only through the subcase test) is
 * the same dividend as {@link NpcTest}: the behaviour lives on the model, so the model's tests own it.
 */
class StandingOfferTest {

    private static final Offer ROOM = new Offer("room", "A room for the night.", 2, "itm6");

    private static Npc keeper(String id) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(SceneId.of("scn5"))
                .shortDescription("A stout innkeeper.")
                .fullDescription("A broad-shouldered woman in a flour-dusted apron.")
                .moveChance(new Chance(0, 1))
                .attackChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(25))
                .hostile(false)
                .offers(List.of(ROOM))
                .build();
    }

    private static StandingOffer ware() {
        return new StandingOffer(keeper("npc2"), ROOM);
    }

    @Test
    void rejects_a_missing_vendor_or_offer() {
        Npc vendor = keeper("npc2");
        assertThatThrownBy(() -> new StandingOffer(null, ROOM))
                .isInstanceOf(InvalidDomainObjectError.class);
        assertThatThrownBy(() -> new StandingOffer(vendor, null))
                .isInstanceOf(InvalidDomainObjectError.class);
    }

    @Test
    void designates_by_the_offer_name_case_insensitively() {
        StandingOffer ware = ware();
        assertThat(ware.matches("room")).isTrue();
        assertThat(ware.matches("ROOM for the NIGHT")).isTrue();
        assertThat(ware.matches("  a room  ")).isTrue();
    }

    @Test
    void never_designates_by_the_vendor_description_or_the_item_handle() {
        StandingOffer ware = ware();
        // The ware is what the player names — the vendor's own description and the delivered item's handle
        // are not designation surfaces.
        assertThat(ware.matches("innkeeper")).isFalse();
        assertThat(ware.matches("itm6")).isFalse();
    }

    @Test
    void id_token_is_the_vendor_id_and_offer_handle_joined_by_the_separator() {
        assertThat(ware().idToken()).isEqualTo("npc2:room");
    }

    @Test
    void has_id_token_compares_against_its_own_composite_token() {
        StandingOffer ware = ware();
        assertThat(ware.hasIdToken("npc2:room")).isTrue();
        assertThat(ware.hasIdToken("npc2:stew")).isFalse();
        assertThat(ware.hasIdToken("npc9:room")).isFalse();
    }

    @Test
    void accepts_its_own_id_token_at_the_shape_gate() {
        assertThatCode(() -> StandingOffer.requireWellFormedToken(ware().idToken()))
                .doesNotThrowAnyException();
    }

    @Test
    void shape_gate_rejects_a_token_without_separator() {
        assertThatThrownBy(() -> StandingOffer.requireWellFormedToken("room"))
                .isInstanceOf(InvalidDomainObjectError.class)
                .hasMessageContaining("<vendor id>")
                .hasMessageContaining("room");
    }

    @Test
    void shape_gate_rejects_a_blank_offer_handle() {
        assertThatThrownBy(() -> StandingOffer.requireWellFormedToken("npc2:"))
                .isInstanceOf(InvalidDomainObjectError.class)
                .hasMessageContaining("offer handle");
        assertThatThrownBy(() -> StandingOffer.requireWellFormedToken("npc2:   "))
                .isInstanceOf(InvalidDomainObjectError.class);
    }

    @Test
    void shape_gate_rejects_a_malformed_vendor_id() {
        // An empty left side fails the NpcId gate the same way any malformed id would.
        assertThatThrownBy(() -> StandingOffer.requireWellFormedToken(":room"))
                .isInstanceOf(InvalidDomainObjectError.class);
    }

    @Test
    void only_the_first_separator_splits_so_a_handle_may_carry_one() {
        // A handle authored with a separator stays a single (oddly named) handle: the token is still well-formed.
        assertThatCode(() -> StandingOffer.requireWellFormedToken("npc2:room:suite"))
                .doesNotThrowAnyException();
    }

    @Test
    void equality_is_by_value() {
        StandingOffer ware = ware();
        assertThat(ware).isEqualTo(ware());
        // The vendor compares by id (Npc equality), the offer by value: an equal offer at the same vendor is
        // the same ware, a different offer under the same handle is not.
        Npc sameVendorOtherFacts = Npc.builder()
                .id(NpcId.of("npc2"))
                .currentScene(SceneId.of("scn3"))
                .shortDescription("A stout innkeeper.")
                .fullDescription("Entirely other prose.")
                .moveChance(new Chance(0, 1))
                .attackChance(new Chance(1, 4))
                .hitPoints(HitPoints.full(11))
                .hostile(true)
                .build();
        assertThat(ware).isEqualTo(new StandingOffer(sameVendorOtherFacts, ROOM));
        assertThat(ware).isNotEqualTo(new StandingOffer(sameVendorOtherFacts,
                new Offer("room", "Another sentence entirely.", 5, "itm1")));
        assertThat(ware).isNotEqualTo(new StandingOffer(keeper("npc9"), ROOM));
        assertThat(ware.hashCode()).isEqualTo(ware().hashCode());
    }
}
