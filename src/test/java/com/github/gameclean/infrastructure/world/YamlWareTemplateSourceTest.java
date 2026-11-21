package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.item.ItemTemplate;
import com.github.gameclean.core.port.ware.WareTemplateSourceOperationsError;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link YamlGameSeedSource}'s ware-template side (#119): the purchase-time pull that assembles
 * a <em>valid-out</em> {@link ItemTemplate} from the authored seed, over the real {@link GameSeedYamlReader}
 * and an in-memory YAML resource — the twin of {@link YamlCorpseBlueprintSourceTest} for the third port. The
 * offered-ware rules the boot gate enforces (neither a container nor anchored) are re-asserted here as thrown
 * {@link WareTemplateSourceOperationsError}s, because at purchase time an unmet rule means <em>drift</em>
 * against an edited seed (an integrity fault), not a presentable authoring outcome. That the minted ware keeps
 * its instance facts while shedding any ground-spawn rule is pinned too: a purchase mints directly into the
 * buyer's keeping, so a spawn rule on a ware is nobody's business at this seam.
 */
class YamlWareTemplateSourceTest {

    private static final String SEED = """
            items:
              - id: itm3
                shortDescription: A copper coin.
                fullDescription: A single dull copper coin.
                currency: true
                spawn:
                  scenes: scn1
                  chance: 1/1
                  max: 3
              - id: itm6
                shortDescription: A small brass key.
                fullDescription: A small brass key on a loop of cord.
                opens: guest-room
              - id: itm4
                shortDescription: A barnacled sea chest.
                fullDescription: A sailor's sea chest, hasp long forced.
                container: true
                portable: true
              - id: itm7
                shortDescription: An oak chest.
                fullDescription: A heavy oak chest banded in black iron.
                container: true
              - id: itm8
                shortDescription: A stone statue of a lantern.
                fullDescription: A lantern carved in grey stone, bolted to the floor.
                portable: false
            """;

    private final GameConfigurationProperties properties =
            mock(GameConfigurationProperties.class, RETURNS_DEEP_STUBS);
    private final YamlGameSeedSource source =
            new YamlGameSeedSource(new GameSeedYamlReader(), properties);

    @Test
    void assemblesTheWareTemplateWithoutSpawnRuleCarryingEveryInstanceFact() {
        givenSeed(SEED);

        // The key: the purchase mints from this very template, so it must arrive as a valid-out template —
        // no ground-spawn rule (ware is minted into hands, never onto a floor) and every instance-copied
        // fact in place: the opens handle here, the currency flag on the coin below.
        ItemTemplate key = source.loadWareTemplate("itm6");

        assertThat(key.getShortDescription()).isEqualTo("A small brass key.");
        assertThat(key.getFullDescription()).isEqualTo("A small brass key on a loop of cord.");
        assertThat(key.getOpens()).isEqualTo("guest-room");
        assertThat(key.isCurrency()).isFalse();
        assertThat(key.spawnsOnGround()).isFalse();
    }

    @Test
    void aCoinWithAGroundSpawnRuleStillResolvesToASpawnRulelessTemplate() {
        givenSeed(SEED);

        // The authored coin spawns on the ground — that rule is the world's, not the ware's: buying a coin
        // (however pointless) must not mint one that could re-spawn, and the currency fact rides over.
        ItemTemplate coin = source.loadWareTemplate("itm3");

        assertThat(coin.isCurrency()).isTrue();
        assertThat(coin.spawnsOnGround()).isFalse();
    }

    @Test
    void throwsThePortErrorWhenTheRefResolvesToNoAuthoredItem() {
        givenSeed(SEED);

        assertThatThrownBy(() -> source.loadWareTemplate("itm99"))
                .isInstanceOf(WareTemplateSourceOperationsError.class)
                .hasMessageContaining("itm99");
    }

    @Test
    void throwsThePortErrorWhenTheRefResolvesToAContainer() {
        givenSeed(SEED);

        // Even a portable chest is a container: ware changes hands as one carryable thing, never a holder.
        assertThatThrownBy(() -> source.loadWareTemplate("itm4"))
                .isInstanceOf(WareTemplateSourceOperationsError.class)
                .hasMessageContaining("itm4");
        assertThatThrownBy(() -> source.loadWareTemplate("itm7"))
                .isInstanceOf(WareTemplateSourceOperationsError.class)
                .hasMessageContaining("itm7");
    }

    @Test
    void throwsThePortErrorWhenTheRefResolvesToAnAnchoredItem() {
        givenSeed(SEED);

        // The gate's kind-sensitive polarity, read the same way toTemplate reads it: an explicit
        // `portable: false` anchors even a plain item, and anchored money or anchored ware cannot change hands.
        assertThatThrownBy(() -> source.loadWareTemplate("itm8"))
                .isInstanceOf(WareTemplateSourceOperationsError.class)
                .hasMessageContaining("itm8");
    }

    @Test
    void wrapsAnUnreadableResourceIntoThePortError() throws IOException {
        Resource broken = mock(Resource.class);
        when(broken.getInputStream()).thenThrow(new IOException("resource gone"));
        when(properties.getWorld().getSeedLocation()).thenReturn(broken);

        assertThatThrownBy(() -> source.loadWareTemplate("itm6"))
                .isInstanceOf(WareTemplateSourceOperationsError.class)
                .hasCauseInstanceOf(IOException.class);
    }

    private void givenSeed(String yaml) {
        when(properties.getWorld().getSeedLocation())
                .thenReturn(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        when(properties.getPlayer().getStartingSceneId()).thenReturn("scn1");
        when(properties.getPlayer().getMaxHitPoints()).thenReturn(30);
    }
}
