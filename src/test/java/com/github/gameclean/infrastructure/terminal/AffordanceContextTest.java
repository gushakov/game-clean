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
    void the_offer_is_empty_before_anything_is_offered() {
        assertThat(context.currentOffer()).isEmpty();
    }

    @Test
    void remembers_the_offered_tokens_in_order() {
        context.offer(List.of("itmAAA", "itmBBB", "itmCCC"));

        assertThat(context.currentOffer()).containsExactly("itmAAA", "itmBBB", "itmCCC");
    }

    @Test
    void clearing_abandons_the_offer() {
        context.offer(List.of("itmAAA"));
        context.clear();

        assertThat(context.currentOffer()).isEmpty();
    }

    @Test
    void a_fresh_offer_replaces_the_previous_one() {
        context.offer(List.of("itmAAA", "itmBBB"));
        context.offer(List.of("itmZZZ"));

        assertThat(context.currentOffer()).containsExactly("itmZZZ");
    }
}
