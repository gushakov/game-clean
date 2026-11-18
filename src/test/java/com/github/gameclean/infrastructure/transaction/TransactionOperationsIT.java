package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.infrastructure.AbstractPostgresIT;
import com.github.gameclean.infrastructure.persistence.item.ItemSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.scene.SceneDbEntity;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link TransactionOperationsOutputPort} against the real Dockerized Postgres. Uses
 * {@code @SpringBootTest} (not a {@code @DataJdbcTest} slice) on purpose: there is no test-managed
 * rollback wrapping each method, so the programmatic transactions genuinely commit and roll back —
 * which is exactly what the rollback-discards-writes guarantee needs in order to be observable. The same
 * holds for the lock-aware result overload: its lost race is a real stale
 * versioned write against the store, so the claim that the loss is raised by the write (not the commit)
 * and comes back as the caller's loss value is proven here rather than assumed.
 *
 * <p>The port has no after-commit or after-rollback hook to exercise: every interaction presents as the
 * statement after its block, so statement order is the after-commit order and a rollback skips what follows
 * by throwing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransactionOperationsIT extends AbstractPostgresIT {

    private static final String ROLLBACK_TEST_ID = "scntxrollbacktest";
    private static final String LOCK_TEST_ITEM_ID = "itmtxlocktest";
    private static final SceneId LOCK_TEST_SCENE = SceneId.of("scntxlocktest");
    private static final PlayerId WINNER = PlayerId.of("plrtxlockwinner");
    private static final PlayerId LOSER = PlayerId.of("plrtxlockloser");

    @Autowired
    private TransactionOperationsOutputPort txOps;

    @Autowired
    private JdbcAggregateTemplate aggregateTemplate;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @Autowired
    private ItemRepositoryOperationsOutputPort itemOps;

    @Autowired
    private ItemSpringDataRepository itemRepository;

    @AfterEach
    void cleanUp() {
        // Defensive: the rollback test should leave nothing behind, but never let a stray row leak.
        sceneRepository.deleteById(ROLLBACK_TEST_ID);
        // The lost-race test commits its item for real (no test-managed rollback here), so remove it.
        itemRepository.deleteById(LOCK_TEST_ITEM_ID);
    }

    @Test
    void returnsTheResultFromWithinTheTransaction() {
        int result = txOps.doInTransactionWithResult(false, () -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void returnsTheLossValueAndRollsBackWhenAVersionedWriteLosesTheRace() {
        itemOps.saveItem(Item.builder()
                .id(ItemId.of(LOCK_TEST_ITEM_ID))
                .location(new Location.OnGround(LOCK_TEST_SCENE))
                .shortDescription("A contested dagger.")
                .fullDescription("Inserted to be raced for by two takers.")
                .build());
        Item loaded = itemOps.findItemsInScene(LOCK_TEST_SCENE).getFirst();   // captures the current version

        // The winner's take commits first and moves the stored version past what `loaded` holds ...
        itemOps.saveItem(loaded.takenBy(WINNER));

        // ... so the loser's write, still carrying the stale version, loses inside the block. The real store
        // raises the loss from the write itself (never from the commit), the transaction rolls back, and the
        // port hands back the loss value instead of raising.
        String settled = txOps.doInTransactionWithResult(
                () -> {
                    itemOps.saveItem(loaded.takenBy(LOSER));
                    return "committed";
                },
                "lost");

        assertThat(settled).isEqualTo("lost");
        assertThat(itemOps.findItemsHeldBy(WINNER)).as("the winner's take stands").hasSize(1);
        assertThat(itemOps.findItemsHeldBy(LOSER)).as("the loser's write was rolled back").isEmpty();
    }

    @Test
    void returnsTheActionsResultWhenTheLockAwareTransactionCommits() {
        String settled = txOps.doInTransactionWithResult(() -> "committed", "lost");
        assertThat(settled).isEqualTo("committed");
    }

    @Test
    void rollbackDiscardsWritesMadeInsideTheTransaction() {
        SceneDbEntity scene = new SceneDbEntity();
        scene.setId(ROLLBACK_TEST_ID);
        scene.setName("Doomed Scene");
        scene.setShortDescription("Will be rolled back.");
        scene.setFullDescription("Inserted inside a transaction that then throws.");
        scene.setExits(Set.of());

        assertThatThrownBy(() -> txOps.doInTransaction(false, () -> {
            aggregateTemplate.insert(scene);
            throw new IllegalStateException("force rollback after the insert");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(sceneRepository.findById(ROLLBACK_TEST_ID))
                .as("the insert was rolled back, so the row is absent")
                .isEmpty();
    }
}
