package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsError;
import com.github.gameclean.core.port.seed.ItemEntry;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link YamlGameSeedSource}'s exception translation. The adapter owns the <em>sourcing</em>,
 * so every technical failure on the way to a {@code GameSeed} must surface as the port's
 * {@link GameSeedSourceOperationsError} — never raw. Two leak classes are pinned: the {@link IOException} of
 * an unreadable resource, and the <em>runtime</em> parse failures the reader raises on a malformed document
 * (a SnakeYAML {@link YAMLException}, a non-numeric chance fraction → {@link NumberFormatException}), which
 * the old {@code catch (IOException)} let escape raw.
 *
 * <p>Also pins the one-parse-per-run memoizer (#95): the reader runs exactly once across both ports' pulls
 * (the death-time blueprint is assembled from the very parse the boot gate validated), and a failed parse is
 * never cached — the next pull retries.
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

    @Test
    void parsesTheSeedOnceAndServesBothPortsFromTheMemoizedParse() {
        GameSeed parsed = seedWithACorpse();
        givenAReadableSeed();
        when(reader.read(any(), eq("scn1"), anyInt())).thenReturn(parsed);

        assertThat(source.loadGameSeed()).isSameAs(parsed);
        assertThat(source.loadGameSeed()).isSameAs(parsed);
        source.loadCorpseBlueprint("crp1");

        verify(reader, times(1)).read(any(), any(), anyInt());
    }

    @Test
    void doesNotCacheAFailedParse() {
        givenAReadableSeed();
        when(reader.read(any(), eq("scn1"), anyInt()))
                .thenThrow(new YAMLException("could not parse document"))
                .thenReturn(seedWithACorpse());

        assertThatThrownBy(source::loadGameSeed).isInstanceOf(GameSeedSourceOperationsError.class);
        assertThat(source.loadGameSeed()).isNotNull();

        verify(reader, times(2)).read(any(), any(), anyInt());
    }

    /** A resource that opens fine, but whose parse blows up with the given runtime failure. */
    private void givenAReadableSeedThatParsesTo(RuntimeException parseFailure) {
        givenAReadableSeed();
        when(reader.read(any(), eq("scn1"), anyInt())).thenThrow(parseFailure);
    }

    private void givenAReadableSeed() {
        when(properties.getWorld().getSeedLocation()).thenReturn(new ByteArrayResource("scenes:".getBytes()));
        when(properties.getPlayer().getStartingSceneId()).thenReturn("scn1");
    }

    /** A parsed seed carrying one authored corpse (a container, no ground-spawn rule) for the blueprint pull. */
    private GameSeed seedWithACorpse() {
        ItemEntry corpse = new ItemEntry("crp1", "A corpse.", "A slain thing lies here.", true, null, null, null);
        return new GameSeed(List.of(), "scn1", 30, List.of(corpse), List.of());
    }
}
