package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.infrastructure.persistence.scene.SceneDbEntity;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link TransactionOperationsOutputPort} against the real Dockerized Postgres. Uses
 * {@code @SpringBootTest} (not a {@code @DataJdbcTest} slice) on purpose: there is no test-managed
 * rollback wrapping each method, so the programmatic transactions genuinely commit and roll back —
 * which is exactly what the after-commit / after-rollback hooks and the rollback-discards-writes
 * guarantee need in order to be observable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransactionOperationsIT {

    private static final String ROLLBACK_TEST_ID = "scntxrollbacktest";

    @Autowired
    private TransactionOperationsOutputPort txOps;

    @Autowired
    private JdbcAggregateTemplate aggregateTemplate;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @AfterEach
    void cleanUp() {
        // Defensive: the rollback test should leave nothing behind, but never let a stray row leak.
        sceneRepository.deleteById(ROLLBACK_TEST_ID);
    }

    @Test
    void runsAfterCommitButNotAfterRollbackWhenTheTransactionCommits() {
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicBoolean rolledBack = new AtomicBoolean(false);

        txOps.doInTransaction(false, () -> {
            txOps.doAfterCommit(() -> committed.set(true));
            txOps.doAfterRollback(() -> rolledBack.set(true));
        });

        assertThat(committed).as("after-commit hook fired").isTrue();
        assertThat(rolledBack).as("after-rollback hook did not fire").isFalse();
    }

    @Test
    void runsAfterRollbackButNotAfterCommitWhenTheActionThrows() {
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicBoolean rolledBack = new AtomicBoolean(false);

        assertThatThrownBy(() -> txOps.doInTransaction(false, () -> {
            txOps.doAfterCommit(() -> committed.set(true));
            txOps.doAfterRollback(() -> rolledBack.set(true));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rolledBack).as("after-rollback hook fired").isTrue();
        assertThat(committed).as("after-commit hook did not fire").isFalse();
    }

    @Test
    void returnsTheResultFromWithinTheTransaction() {
        int result = txOps.doInTransactionWithResult(false, () -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void runsAfterCommitImmediatelyAndAfterRollbackAsNoOpOutsideAnyTransaction() {
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicBoolean rolledBack = new AtomicBoolean(false);

        txOps.doAfterCommit(() -> committed.set(true));
        txOps.doAfterRollback(() -> rolledBack.set(true));

        assertThat(committed).as("after-commit runs immediately with no active transaction").isTrue();
        assertThat(rolledBack).as("after-rollback is a no-op with no active transaction").isFalse();
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
