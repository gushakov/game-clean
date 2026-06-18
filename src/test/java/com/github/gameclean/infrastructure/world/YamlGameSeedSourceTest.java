package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.port.seed.GameSeedSourceOperationsError;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link YamlGameSeedSource}'s exception translation. The adapter owns the <em>sourcing</em>,
 * so every technical failure on the way to a {@code GameSeed} must surface as the port's
 * {@link GameSeedSourceOperationsError} — never raw. Two leak classes are pinned: the {@link IOException} of
 * an unreadable resource, and the <em>runtime</em> parse failures the reader raises on a malformed document
 * (a SnakeYAML {@link YAMLException}, a non-numeric chance fraction → {@link NumberFormatException}), which
 * the old {@code catch (IOException)} let escape raw.
 */
class YamlGameSeedSourceTest {

    private final GameSeedYamlReader reader = mock(GameSeedYamlReader.class);
    private final GameConfigurationProperties properties = mock(GameConfigurationProperties.class, RETURNS_DEEP_STUBS);
    private final YamlGameSeedSource source = new YamlGameSeedSource(reader, properties);

    @Test
    void wrapsAnUnreadableResourceIoExceptionIntoThePortType() throws IOException {
        Resource broken = mock(Resource.class);
        when(broken.getInputStream()).thenThrow(new IOException("resource gone"));
        when(properties.getWorld().getSeedLocation()).thenReturn(broken);
        when(properties.getPlayer().getStartingSceneId()).thenReturn("scn1");

        assertThatThrownBy(source::loadGameSeed)
                .isInstanceOf(GameSeedSourceOperationsError.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsAMalformedYamlParseFailureIntoThePortType() {
        givenAReadableSeedThatParsesTo(new YAMLException("could not parse document"));

        assertThatThrownBy(source::loadGameSeed)
                .isInstanceOf(GameSeedSourceOperationsError.class)
                .hasCauseInstanceOf(YAMLException.class);
    }

    @Test
    void wrapsAMalformedChanceFractionIntoThePortType() {
        givenAReadableSeedThatParsesTo(new NumberFormatException("For input string: \"half\""));

        assertThatThrownBy(source::loadGameSeed)
                .isInstanceOf(GameSeedSourceOperationsError.class)
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    /** A resource that opens fine, but whose parse blows up with the given runtime failure. */
    private void givenAReadableSeedThatParsesTo(RuntimeException parseFailure) {
        when(properties.getWorld().getSeedLocation()).thenReturn(new ByteArrayResource("scenes:".getBytes()));
        when(properties.getPlayer().getStartingSceneId()).thenReturn("scn1");
        when(reader.read(any(), eq("scn1"))).thenThrow(parseFailure);
    }
}
