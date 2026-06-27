package com.github.gameclean.infrastructure.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the session-lifetime disambiguation buffer: it remembers the offered id tokens in order, exposes
 * them whole via {@link AffordanceContext#currentOffer()} (the console hands them to the use case as a value —
 * the buffer resolves nothing and decides nothing), and clearing (or a fresh offer) replaces the state. The
 * buffer trades in raw id tokens, not the {@code ItemId} model VO — see {@link AffordanceContext}.
 */
class AffordanceContextTest {

    private final AffordanceContext context = new AffordanceContext();

    @Test
    void the_offer_is_empty_and_unkinded_before_anything_is_offered() {
        assertThat(context.currentOffer()).isEmpty();
        assertThat(context.kind()).isNull();
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
}
