package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import com.github.gameclean.infrastructure.AbstractPostgresIT;
import com.github.gameclean.infrastructure.persistence.clock.GameClockSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.daytime.DayPhaseLogSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.item.ItemSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.npc.NpcDbEntity;
import com.github.gameclean.infrastructure.persistence.npc.NpcSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.player.PlayerSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration of the {@code InitializeGame} vertical against the real Dockerized Postgres,
 * driven directly through the composition root: the use case, the real {@code YamlGameSeedSource} (which
 * pulls and parses {@code world/scenes.yaml}), the {@code SpringSceneRepositoryAdapter} and
 * {@code SpringPlayerRepositoryAdapter}, the transaction adapter, the MapStruct mappers and the
 * Flyway-migrated schema all participate. The outcome is asserted through the <em>persisted state</em>: the
 * presenter is not a bean (the composition root {@code new}s it), so it cannot be replaced by a mock here.
 * The presenter-outcome contract is pinned by {@code InitializeGameUseCaseTest}; this test proves the real
 * writes and their idempotency across two runs — including that the seed is now pulled by the use case
 * itself, not pushed in by the test.
 *
 * <p>{@code @SpringBootTest} (not a {@code @DataJdbcTest} slice) so the programmatic transactions genuinely
 * commit — there is no test-managed rollback. The test cleans up after itself in {@code @AfterEach};
 * deleting a scene removes its owned exits with it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InitializeGameIT extends AbstractPostgresIT {

    private static final List<String> SEED_IDS = List.of("scn1", "scn2", "scn3", "scn4", "scn5", "scn6");
    private static final String SEEDED_PLAYER_ID = "plr1";

    @Autowired
    private InitializeGameInputPort initializeGame;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @Autowired
    private PlayerSpringDataRepository playerRepository;

    @Autowired
    private ItemSpringDataRepository itemRepository;

    @Autowired
    private NpcSpringDataRepository npcRepository;

    @Autowired
    private GameClockSpringDataRepository gameClockRepository;

    @Autowired
    private DayPhaseLogSpringDataRepository dayPhaseLogRepository;

    @AfterEach
    void cleanUp() {
        itemRepository.deleteAll();
        npcRepository.deleteAll();
        gameClockRepository.deleteAll();
        dayPhaseLogRepository.deleteAll();
        playerRepository.deleteById(SEEDED_PLAYER_ID);
        SEED_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void initializesTheWorldPlayerAndItemsThenStaysIdempotentOnASecondRun() {
        // first run against an empty game: the use case pulls the authored seed, seeds the six scenes,
        // places the player, and spawns the authored items (a random count, but deterministically idempotent) ...
        initializeGame.systemInitializesGame();

        assertThat(sceneRepository.count()).isEqualTo(6);
        assertThat(sceneRepository.findById("scn2")).isPresent();
        assertThat(playerRepository.findById(SEEDED_PLAYER_ID)).isPresent()
                .get().satisfies(player -> assertThat(player.getCurrentSceneId()).isEqualTo("scn1"));
        // the world clock is created at time zero ...
        assertThat(gameClockRepository.count()).isEqualTo(1);
        assertThat(gameClockRepository.findAll().iterator().next().getAccumulatedGameSeconds()).isZero();
        // ... and the day-phase log is seeded at the "nothing announced" sentinel (-1) ...
        assertThat(dayPhaseLogRepository.count()).isEqualTo(1);
        assertThat(dayPhaseLogRepository.findAll().iterator().next().getAnnouncedThroughHour()).isEqualTo(-1);
        long itemsAfterFirstRun = itemRepository.count();
        // the authored hooded wanderer (chance 1/1, max 1, scn1) and innkeeper (chance 1/1, max 1, scn5) are each
        // spawned exactly once, where authored ...
        assertThat(npcRepository.count()).isEqualTo(2);
        assertThat(npcRepository.findAll()).extracting(NpcDbEntity::getCurrentSceneId)
                .containsExactlyInAnyOrder("scn1", "scn5");
        // ... the inn's stair up carries its lock, and the brass key that opens it lies on the inn floor ...
        assertThat(sceneRepository.findById("scn5").orElseThrow().getExits())
                .filteredOn(exit -> exit.getName().equals("up"))
                .singleElement()
                .satisfies(exit -> assertThat(exit.getLock().getRef()).isEqualTo("guest-room"));
        assertThat(itemRepository.findAll()).filteredOn(item -> "guest-room".equals(item.getOpens()))
                .singleElement()
                .satisfies(key -> assertThat(key.getLocation().getRef()).isEqualTo("scn5"));

        // ... a second run finds world, player, items and NPCs already present and writes no duplicate rows —
        // notably the spawns are not re-rolled, so the item and NPC counts are unchanged.
        initializeGame.systemInitializesGame();

        assertThat(sceneRepository.count()).isEqualTo(6);
        assertThat(playerRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(itemsAfterFirstRun);
        assertThat(npcRepository.count()).isEqualTo(2);
        // the clock and the day-phase log are not re-created and stay single rows
        assertThat(gameClockRepository.count()).isEqualTo(1);
        assertThat(dayPhaseLogRepository.count()).isEqualTo(1);
    }
}
