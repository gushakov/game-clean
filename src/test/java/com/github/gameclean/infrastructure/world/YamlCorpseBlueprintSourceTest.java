package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.port.corpse.CorpseBlueprint;
import com.github.gameclean.core.port.corpse.CorpseBlueprintSourceOperationsError;
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
 * Unit tests for {@link YamlGameSeedSource}'s corpse-blueprint side (#93): the death-time pull that assembles
 * a <em>valid-out</em> {@link CorpseBlueprint} — real templates and odds — from the authored seed, over the
 * real {@link GameSeedYamlReader} and an in-memory YAML resource. The strict corpse-template rules the boot
 * gate enforces are re-asserted here as thrown {@link CorpseBlueprintSourceOperationsError}s, because at death
 * time an unmet rule means <em>drift</em> against an edited seed (an integrity fault), not a presentable
 * authoring outcome. The gate's kind-sensitive portability polarity and the no-ground-spawn-rule-on-blueprint
 * decision are pinned too.
 */
class YamlCorpseBlueprintSourceTest {

    private static final String SEED = """
            items:
              - id: itm1
                shortDescription: A tarnished silver ring.
                fullDescription: A slim band of tarnished silver.
                spawn:
                  scenes: scn1
                  chance: 1/2
                  max: 2
              - id: itm5
                shortDescription: The corpse of a hooded wanderer.
                fullDescription: The wanderer lies where it fell.
                container: true
                contains:
                  - item: itm1
                    chance: 1/2
              - id: itm6
                shortDescription: A barnacled sea chest.
                fullDescription: A sailor's sea chest, hasp long forced.
                container: true
                portable: true
              - id: itm7
                shortDescription: An oak chest.
                fullDescription: A heavy oak chest banded in black iron.
                container: true
                spawn:
                  scenes: scn1
                  chance: 1/1
                  max: 1
              - id: itm8
                shortDescription: A corpse with dangling loot.
                fullDescription: Its loot ref resolves to nothing.
                container: true
                contains:
                  - item: itm99
                    chance: 1/2
            """;

    private final GameConfigurationProperties properties =
            mock(GameConfigurationProperties.class, RETURNS_DEEP_STUBS);
    private final YamlGameSeedSource source =
            new YamlGameSeedSource(new GameSeedYamlReader(), properties);

    @Test
    void assemblesTheBlueprintWithTheCorpseTemplateAndItsLootOdds() {
        givenSeed(SEED);

        CorpseBlueprint blueprint = source.loadCorpseBlueprint("itm5");

        // The corpse template: a container, anchored by the kind default (no `portable:` authored), never
        // ground-spawning (a blueprint is a minting recipe for one death).
        assertThat(blueprint.getCorpseTemplate().getShortDescription())
                .isEqualTo("The corpse of a hooded wanderer.");
        assertThat(blueprint.getCorpseTemplate().isContainer()).isTrue();
        assertThat(blueprint.getCorpseTemplate().isAnchored()).isTrue();
        assertThat(blueprint.getCorpseTemplate().spawnsOnGround()).isFalse();
        // The loot: the ring's template paired with the corpse's authored odds. The ring's own ground-spawn
        // rule is deliberately dropped (it is not this recipe's business), and a plain item is portable.
        assertThat(blueprint.getLoot()).singleElement().satisfies(loot -> {
            assertThat(loot.getTemplate().getShortDescription()).isEqualTo("A tarnished silver ring.");
            assertThat(loot.getTemplate().isContainer()).isFalse();
            assertThat(loot.getTemplate().isAnchored()).isFalse();
            assertThat(loot.getTemplate().spawnsOnGround()).isFalse();
            assertThat(loot.getChance()).isEqualTo(new Chance(1, 2));
        });
    }

    @Test
    void anAuthoredPortableTrueCorpseResolvesToNotAnchored() {
        givenSeed(SEED);

        CorpseBlueprint blueprint = source.loadCorpseBlueprint("itm6");

        // The same authoring polarity the boot gate applies: `portable: true` overrides the container default.
        assertThat(blueprint.getCorpseTemplate().isAnchored()).isFalse();
        assertThat(blueprint.getLoot()).isEmpty();
    }

    @Test
    void throwsThePortErrorWhenTheRefResolvesToNoAuthoredItem() {
        givenSeed(SEED);

        assertThatThrownBy(() -> source.loadCorpseBlueprint("itm99"))
                .isInstanceOf(CorpseBlueprintSourceOperationsError.class)
                .hasMessageContaining("itm99");
    }

    @Test
    void throwsThePortErrorWhenTheRefResolvesToANonContainer() {
        givenSeed(SEED);

        assertThatThrownBy(() -> source.loadCorpseBlueprint("itm1"))
                .isInstanceOf(CorpseBlueprintSourceOperationsError.class)
                .hasMessageContaining("itm1");
    }

    @Test
    void throwsThePortErrorWhenTheRefResolvesToAGroundSpawningContainer() {
        givenSeed(SEED);

        assertThatThrownBy(() -> source.loadCorpseBlueprint("itm7"))
                .isInstanceOf(CorpseBlueprintSourceOperationsError.class)
                .hasMessageContaining("itm7");
    }

    @Test
    void throwsThePortErrorWhenALootRefDoesNotResolve() {
        givenSeed(SEED);

        assertThatThrownBy(() -> source.loadCorpseBlueprint("itm8"))
                .isInstanceOf(CorpseBlueprintSourceOperationsError.class)
                .hasMessageContaining("itm99");
    }

    @Test
    void wrapsAnUnreadableResourceIntoThePortError() throws IOException {
        Resource broken = mock(Resource.class);
        when(broken.getInputStream()).thenThrow(new IOException("resource gone"));
        when(properties.getWorld().getSeedLocation()).thenReturn(broken);

        assertThatThrownBy(() -> source.loadCorpseBlueprint("itm5"))
                .isInstanceOf(CorpseBlueprintSourceOperationsError.class)
                .hasCauseInstanceOf(IOException.class);
    }

    private void givenSeed(String yaml) {
        when(properties.getWorld().getSeedLocation())
                .thenReturn(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        when(properties.getPlayer().getStartingSceneId()).thenReturn("scn1");
        when(properties.getPlayer().getMaxHitPoints()).thenReturn(30);
    }
}
