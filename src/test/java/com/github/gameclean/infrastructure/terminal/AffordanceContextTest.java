package com.github.gameclean.infrastructure.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the session-lifetime conversational buffer: a pure single-slot holder that arms the one
 * {@link Affordance} — a {@link SelectionAffordance} (offered id tokens in order) or an
 * {@link EphemeralAffordance} (an opaque payload) — exposes it whole via {@link AffordanceContext#current()}
 * (the console hands it to the resuming conversation as a value — the buffer resolves nothing and decides
 * nothing), and clears (or a fresh arming) replaces the state. Token offers trade in raw id tokens, not model
 * VOs; the payload is opaque to the shell — see {@link Affordance}.
 */
class AffordanceContextTest {

    private final AffordanceContext context = new AffordanceContext();

    @Test
    void the_buffer_is_empty_before_anything_is_armed() {
        assertThat(context.current()).isNull();
    }

    @Test
    void remembers_the_offered_tokens_in_order_tagged_with_their_kind() {
        context.offer(AffordanceKind.EXAMINE, List.of("itmAAA", "itmBBB", "itmCCC"));

        Affordance armed = context.current();
        assertThat(armed).isInstanceOf(SelectionAffordance.class);
        assertThat(armed.getKind()).isEqualTo(AffordanceKind.EXAMINE);
        assertThat(((SelectionAffordance) armed).getTokens()).containsExactly("itmAAA", "itmBBB", "itmCCC");
    }

    @Test
    void clearing_abandons_the_offer_and_its_kind() {
        context.offer(AffordanceKind.TAKE, List.of("itmAAA"));
        context.clear();

        assertThat(context.current()).isNull();
    }

    @Test
    void a_fresh_offer_replaces_the_previous_one_kind_and_all() {
        context.offer(AffordanceKind.EXAMINE, List.of("itmAAA", "itmBBB"));
        context.offer(AffordanceKind.TAKE, List.of("itmZZZ"));

        Affordance armed = context.current();
        assertThat(armed.getKind()).isEqualTo(AffordanceKind.TAKE);
        assertThat(((SelectionAffordance) armed).getTokens()).containsExactly("itmZZZ");
    }

    @Test
    void arms_an_opaque_payload_tagged_with_its_kind() {
        Object envelope = new Object();
        context.arm(AffordanceKind.BLACKJACK, envelope);

        Affordance armed = context.current();
        assertThat(armed).isInstanceOf(EphemeralAffordance.class);
        assertThat(armed.getKind()).isEqualTo(AffordanceKind.BLACKJACK);
        assertThat(((EphemeralAffordance) armed).getPayload()).isSameAs(envelope);
    }

    @Test
    void arming_a_payload_replaces_a_token_offer_and_vice_versa() {
        context.offer(AffordanceKind.EXAMINE, List.of("itmAAA"));
        context.arm(AffordanceKind.BLACKJACK, new Object());
        assertThat(context.current()).isInstanceOf(EphemeralAffordance.class);
        assertThat(context.current().getKind()).isEqualTo(AffordanceKind.BLACKJACK);

        context.offer(AffordanceKind.TAKE, List.of("itmZZZ"));
        assertThat(context.current()).isInstanceOf(SelectionAffordance.class);
        assertThat(context.current().getKind()).isEqualTo(AffordanceKind.TAKE);
    }

    @Test
    void clearing_disarms_a_payload_affordance_too() {
        context.arm(AffordanceKind.BLACKJACK, new Object());
        context.clear();

        assertThat(context.current()).isNull();
    }
}
