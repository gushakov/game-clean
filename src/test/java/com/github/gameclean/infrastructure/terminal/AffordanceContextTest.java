package com.github.gameclean.infrastructure.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the session-lifetime disambiguation buffer: a non-empty buffer is "selection pending", a 1-based
 * number resolves to the offered id token, out-of-range and nothing-pending both miss, and clearing (or a
 * fresh offer) replaces the state. The buffer trades in raw id tokens, not the {@code ItemId} model VO — see
 * {@link AffordanceContext}.
 */
class AffordanceContextTest {

    private final AffordanceContext context = new AffordanceContext();

    @Test
    void nothing_is_pending_before_an_offer() {
        assertThat(context.hasPending()).isFalse();
        assertThat(context.resolve(1)).isEmpty();
    }

    @Test
    void resolves_a_one_based_number_to_the_offered_token() {
        context.offer(List.of("itmAAA", "itmBBB", "itmCCC"));

        assertThat(context.hasPending()).isTrue();
        assertThat(context.resolve(1)).contains("itmAAA");
        assertThat(context.resolve(2)).contains("itmBBB");
        assertThat(context.resolve(3)).contains("itmCCC");
    }

    @Test
    void a_number_out_of_range_misses_but_leaves_the_offer_pending() {
        context.offer(List.of("itmAAA"));

        assertThat(context.resolve(0)).isEmpty();
        assertThat(context.resolve(2)).isEmpty();
        assertThat(context.hasPending()).isTrue();
    }

    @Test
    void clearing_abandons_the_offer() {
        context.offer(List.of("itmAAA"));
        context.clear();

        assertThat(context.hasPending()).isFalse();
        assertThat(context.resolve(1)).isEmpty();
    }

    @Test
    void a_fresh_offer_replaces_the_previous_one() {
        context.offer(List.of("itmAAA", "itmBBB"));
        context.offer(List.of("itmZZZ"));

        assertThat(context.resolve(1)).contains("itmZZZ");
        assertThat(context.resolve(2)).isEmpty();
    }
}
