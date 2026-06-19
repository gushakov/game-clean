package com.github.gameclean.infrastructure.player;

import com.github.gameclean.infrastructure.GameConfigurationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link ConfiguredPlayerAdapter}: it returns the configured player id verbatim, as a
 * primitive — validation is the use case's job, not the adapter's.
 */
class ConfiguredPlayerAdapterTest {

    @Test
    void returnsTheConfiguredPlayerId() {
        GameConfigurationProperties properties = new GameConfigurationProperties(
                null, null, new GameConfigurationProperties.Player("plr1", "scn1"), null);
        ConfiguredPlayerAdapter adapter = new ConfiguredPlayerAdapter(properties);

        assertThat(adapter.currentPlayerId()).isEqualTo("plr1");
    }
}
