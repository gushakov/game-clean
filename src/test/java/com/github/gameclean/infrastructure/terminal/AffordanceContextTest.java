package com.github.gameclean.infrastructure.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the session-lifetime conversational buffer: it holds the one armed {@link Affordance} — a
 * selection's offered id tokens in order, or an ephemeral conversation's opaque payload — exposes it whole
 * (the console hands it to the resuming conversation as a value — the buffer resolves nothing and decides
 * nothing), and clearing (or a fresh arming) replaces the state. Token offers trade in raw id tokens, not
 * model VOs; the payload is opaque to the shell — see {@link Affordance}.
 */
class AffordanceContextTest {

    private final AffordanceContext context = new AffordanceContext();

    @Test
    void the_buffer_is_empty_and_unkinded_before_anything_is_armed() {
        assertThat(context.currentOffer()).isEmpty();
        assertThat(context.kind()).isNull();
        assertThat(context.current()).isNull();
    }

    @Test
    void remembers_the_offered_tokens_in_order_tagged_with_their_kind() {
        context.offer(SelectionKind.EXAMINE, List.of("itmAAA", "itmBBB", "itmCCC"));

        assertThat(context.currentOffer()).containsExactly("itmAAA", "itmBBB", "itmCCC");
        assertThat(context.kind()).isEqualTo(SelectionKind.EXAMINE);
    }

    @Test
    void clearing_abandons_the_offer_and_its_kind() {
        context.offer(SelectionKind.TAKE, List.of("itmAAA"));
        context.clear();

        assertThat(context.currentOffer()).isEmpty();
        assertThat(context.kind()).isNull();
    }

    @Test
    void a_fresh_offer_replaces_the_previous_one_kind_and_all() {
        context.offer(SelectionKind.EXAMINE, List.of("itmAAA", "itmBBB"));
        context.offer(SelectionKind.TAKE, List.of("itmZZZ"));

        assertThat(context.currentOffer()).containsExactly("itmZZZ");
        assertThat(context.kind()).isEqualTo(SelectionKind.TAKE);
    }

    @Test
    void arms_an_opaque_payload_tagged_with_its_kind_and_no_tokens() {
        Object envelope = new Object();
        context.arm(SelectionKind.BLACKJACK, envelope);

        assertThat(context.kind()).isEqualTo(SelectionKind.BLACKJACK);
        assertThat(context.currentOffer()).isEmpty();
        assertThat(context.current().getPayload()).isSameAs(envelope);
    }

    @Test
    void arming_a_payload_replaces_a_token_offer_and_vice_versa() {
        context.offer(SelectionKind.EXAMINE, List.of("itmAAA"));
        context.arm(SelectionKind.BLACKJACK, new Object());
        assertThat(context.kind()).isEqualTo(SelectionKind.BLACKJACK);

        context.offer(SelectionKind.TAKE, List.of("itmZZZ"));
        assertThat(context.kind()).isEqualTo(SelectionKind.TAKE);
        assertThat(context.current().getPayload()).isNull();
    }

    @Test
    void clearing_disarms_a_payload_affordance_too() {
        context.arm(SelectionKind.BLACKJACK, new Object());
        context.clear();

        assertThat(context.current()).isNull();
        assertThat(context.kind()).isNull();
    }
}
