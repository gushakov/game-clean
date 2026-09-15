package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.infrastructure.AbstractPostgresIT;
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
 * which is exactly what the after-commit hook and the rollback-discards-writes guarantee need in order
 * to be observable.
 *
 * <p>The port has no after-rollback hook to exercise: Spring implements one only as
 * {@code afterCompletion(STATUS_ROLLED_BACK)}, whose {@code catch (Throwable)} would swallow a failing
 * action, so the method was retired rather than kept un-fail-loud. Rollback-side presentation happens from
 * an ordinary {@code catch} outside {@code doInTransaction} instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransactionOperationsIT extends AbstractPostgresIT {

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
    void runsAfterCommitWhenTheTransactionCommits() {
        AtomicBoolean committed = new AtomicBoolean(false);

        txOps.doInTransaction(false, () -> txOps.doAfterCommit(() -> committed.set(true)));

        assertThat(committed).as("after-commit hook fired").isTrue();
    }

    @Test
    void doesNotRunAfterCommitWhenTheActionThrows() {
        AtomicBoolean committed = new AtomicBoolean(false);

        assertThatThrownBy(() -> txOps.doInTransaction(false, () -> {
            txOps.doAfterCommit(() -> committed.set(true));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(committed).as("after-commit hook did not fire on a rolled-back transaction").isFalse();
    }

    @Test
    void propagatesAThrowingAfterCommitActionOverTheRealTransactionManager() {
        IllegalStateException fromDeferredAction = new IllegalStateException("presenter blew up after commit");

        // The fail-loud contract against the real manager: a deferred action that throws must reach the
        // caller of doInTransaction rather than being logged and swallowed, and must arrive as itself —
        // not wrapped in TransactionOperationsError, since it is no failure of the demarcation machinery.
        assertThatThrownBy(() -> txOps.doInTransaction(false, () -> txOps.doAfterCommit(() -> {
            throw fromDeferredAction;
        }))).isSameAs(fromDeferredAction);
    }

    @Test
    void returnsTheResultFromWithinTheTransaction() {
        int result = txOps.doInTransactionWithResult(false, () -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void runsAfterCommitImmediatelyOutsideAnyTransaction() {
        AtomicBoolean committed = new AtomicBoolean(false);

        txOps.doAfterCommit(() -> committed.set(true));

        assertThat(committed).as("after-commit runs immediately with no active transaction").isTrue();
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
