package com.github.gameclean.core.model.combat;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for the {@link HitPoints} value object: the always-valid construction gate (positive max, current in
 * {@code [0, max]}), the {@code full} spawn factory, and {@link HitPoints#damage(int)} — which lowers
 * {@code current} but clamps it at zero (overkill is not negative health), the domain rule behind an NPC being
 * dead.
 */
class HitPointsTest {

    @Test
    void full_is_current_equal_to_max() {
        HitPoints hp = HitPoints.full(10);
        assertThat(hp.getCurrent()).isEqualTo(10);
        assertThat(hp.getMax()).isEqualTo(10);
        assertThat(hp.isDepleted()).isFalse();
    }

    @Test
    void damage_lowers_current_leaving_max_unchanged() {
        HitPoints hp = HitPoints.full(10).damage(4);
        assertThat(hp).isEqualTo(new HitPoints(6, 10));
        assertThat(hp.isDepleted()).isFalse();
    }

    @Test
    void damage_clamps_current_at_zero_on_overkill() {
        HitPoints hp = new HitPoints(3, 10).damage(9);
        assertThat(hp).isEqualTo(new HitPoints(0, 10));
        assertThat(hp.isDepleted()).isTrue();
    }

    @Test
    void damage_of_exactly_current_depletes_the_pool() {
        assertThat(new HitPoints(5, 10).damage(5).isDepleted()).isTrue();
    }

    @Test
    void damage_is_immutable_copy_on_write() {
        HitPoints original = HitPoints.full(10);
        original.damage(4);
        assertThat(original).isEqualTo(HitPoints.full(10));
    }

    @Test
    void rejects_a_non_positive_max() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new HitPoints(0, 0));
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new HitPoints(0, -1));
    }

    @Test
    void rejects_a_negative_current() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new HitPoints(-1, 10));
    }

    @Test
    void rejects_current_exceeding_max() {
        assertThatExceptionOfType(InvalidDomainObjectError.class).isThrownBy(() -> new HitPoints(11, 10));
    }

    @Test
    void damage_rejects_a_negative_amount_as_a_caller_bug() {
        assertThatIllegalArgumentException().isThrownBy(() -> HitPoints.full(10).damage(-1));
    }
}
