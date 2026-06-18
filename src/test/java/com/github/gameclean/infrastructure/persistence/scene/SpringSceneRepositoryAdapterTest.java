package com.github.gameclean.infrastructure.persistence.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Unit tests (DB-free) for {@link SpringSceneRepositoryAdapter}'s exception translation — the representative
 * of the three persistence adapters, which all share the catch discipline. Three things are pinned:
 *
 * <ul>
 *   <li>a Spring {@link org.springframework.dao.DataAccessException} (read or write) becomes a
 *       {@link PersistenceOperationsError};</li>
 *   <li>a reconstitution {@link InvalidDomainObjectError} on the read path — a <em>corrupt stored row</em>
 *       failing the validating constructors — is an integrity fault of this port and is wrapped into a
 *       {@link PersistenceOperationsError}, <em>not</em> left as a domain-input invalidity;</li>
 *   <li>a stray programming bug (a non-{@code DataAccessException} runtime) rides raw to the caller rather
 *       than being mislabelled as a persistence fault.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SpringSceneRepositoryAdapterTest {

    @Mock
    private SceneSpringDataRepository repository;
    @Mock
    private JdbcAggregateTemplate aggregateTemplate;
    @Mock
    private SceneDbEntityMapper mapper;

    @InjectMocks
    private SpringSceneRepositoryAdapter adapter;

    @Test
    void findScene_wraps_a_data_access_exception_into_the_port_type() {
        when(repository.findById("scn1")).thenThrow(new DataRetrievalFailureException("connection reset"));

        assertThatThrownBy(() -> adapter.findScene(new SceneId("scn1")))
                .isInstanceOf(PersistenceOperationsError.class)
                .hasCauseInstanceOf(DataRetrievalFailureException.class);
    }

    @Test
    void findScene_wraps_a_corrupt_row_reconstitution_failure_as_an_integrity_fault() {
        SceneDbEntity corrupt = new SceneDbEntity();
        when(repository.findById("scn1")).thenReturn(Optional.of(corrupt));
        when(mapper.toDomain(corrupt)).thenThrow(new InvalidDomainObjectError("scene name must not be blank"));

        assertThatThrownBy(() -> adapter.findScene(new SceneId("scn1")))
                .isInstanceOf(PersistenceOperationsError.class)
                .hasCauseInstanceOf(InvalidDomainObjectError.class);
    }

    @Test
    void findScene_lets_a_stray_bug_propagate_raw_rather_than_mislabelling_it() {
        SceneDbEntity entity = new SceneDbEntity();
        when(repository.findById("scn1")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenThrow(new IllegalStateException("mapper bug"));

        assertThatThrownBy(() -> adapter.findScene(new SceneId("scn1")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(PersistenceOperationsError.class);
    }

    @Test
    void saveScene_wraps_a_data_access_exception_into_the_port_type() {
        Scene scene = Scene.builder()
                .id(new SceneId("scn1"))
                .name("Old Gate")
                .shortDescription("A weathered archway.")
                .fullDescription("The gate's iron hinges have long since rusted shut.")
                .exits(List.of())
                .build();
        when(mapper.toDbEntity(scene)).thenReturn(new SceneDbEntity());
        doThrow(new DataIntegrityViolationException("duplicate key")).when(aggregateTemplate).insert(any());

        assertThatThrownBy(() -> adapter.saveScene(scene))
                .isInstanceOf(PersistenceOperationsError.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
