package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the {@link Offer} value object — its always-valid construction (a non-blank handle, name and item
 * handle, a price of at least one coin) and {@link Offer#requireUniqueRefs(List)}, the list-wide rule its two
 * owners ({@link Npc} and {@link NpcTemplate}) run over the wares they are handed. Pinning these directly here,
 * rather than only through the owners' tests, is the testability dividend of keeping the gate on the value object:
 * a stored row reconstituted by the mapper passes through this gate too, with no NPC involved.
 */
class OfferTest {

    private static Offer room() {
        return new Offer("room", "A room for the night.", 2, "itm6");
    }

    @Test
    void equality_is_by_value() {
        assertThat(room()).isEqualTo(new Offer("room", "A room for the night.", 2, "itm6"));
        assertThat(room()).isNotEqualTo(new Offer("room", "A room for the night.", 3, "itm6"));
        assertThat(room()).isNotEqualTo(new Offer("room", "A room for the night.", 2, "itm7"));
    }

    @Test
    void rejects_a_blank_or_missing_ref() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer("  ", "A room for the night.", 2, "itm6"));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer(null, "A room for the night.", 2, "itm6"))
                .withMessageContaining("offer ref must not be null");
    }

    @Test
    void rejects_a_blank_or_missing_name() {
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer("room", "   ", 2, "itm6"));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer("room", null, 2, "itm6"))
                .withMessageContaining("offer name must not be null");
    }

    @Test
    void rejects_a_price_below_one_coin() {
        // A free offer sells nothing, and a negative price pays the player to take the ware: both are authoring
        // mistakes, rejected rather than modeled around. One coin is the smallest valid price.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer("room", "A room for the night.", 0, "itm6"))
                .withMessageContaining("at least 1");
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer("room", "A room for the night.", -1, "itm6"));
        assertThat(new Offer("room", "A room for the night.", 1, "itm6").getPrice()).isEqualTo(1);
    }

    @Test
    void rejects_a_blank_or_missing_item_ref() {
        // An offer that hands over nothing is an authoring mistake — the missing `item:` of the seed arrives here
        // as null and is refused by the same gate as a blank one.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer("room", "A room for the night.", 2, " "));
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> new Offer("room", "A room for the night.", 2, null))
                .withMessageContaining("offer item ref must not be null");
    }

    @Test
    void accepts_an_empty_or_distinctly_handled_list() {
        // An NPC with no wares trivially satisfies the uniqueness rule; two handles apart satisfy it too.
        assertThatCode(() -> Offer.requireUniqueRefs(List.of())).doesNotThrowAnyException();
        assertThatCode(() -> Offer.requireUniqueRefs(List.of(
                room(), new Offer("stew", "A bowl of yesterday's stew.", 1, "itm1"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejects_two_offers_sharing_a_handle() {
        // The handle is the offer's half of the selection token, so it can mean only one thing within one NPC.
        assertThatExceptionOfType(InvalidDomainObjectError.class)
                .isThrownBy(() -> Offer.requireUniqueRefs(List.of(
                        room(), new Offer("room", "A bowl of yesterday's stew.", 1, "itm1"))))
                .withMessageContaining("room");
    }
}
