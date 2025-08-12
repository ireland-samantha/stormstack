package com.lightningfirefly.engine.internal.core.entity;

import com.lightningfirefly.engine.core.entity.CoreComponents;
import com.lightningfirefly.engine.core.entity.EntityFactory;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DefaultEntityFactory}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultEntityFactory")
class DefaultEntityFactoryTest {

    @Mock
    private EntityComponentStore store;

    private DefaultEntityFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DefaultEntityFactory(store);
    }

    @Nested
    @DisplayName("createEntity(long matchId)")
    class CreateEntitySimple {

        @Test
        @DisplayName("should create entity and attach MATCH_ID component")
        void shouldCreateEntityAndAttachMatchId() {
            long matchId = 42L;

            long entityId = factory.createEntity(matchId);

            verify(store).createEntity(entityId);
            verify(store).attachComponent(entityId, CoreComponents.MATCH_ID, matchId);
        }

        @Test
        @DisplayName("should attach ENTITY_ID component with entity ID value")
        void shouldAttachEntityIdComponent() {
            long matchId = 42L;

            long entityId = factory.createEntity(matchId);

            verify(store).attachComponent(entityId, CoreComponents.ENTITY_ID, entityId);
        }

        @Test
        @DisplayName("should generate unique entity IDs")
        void shouldGenerateUniqueEntityIds() {
            long id1 = factory.createEntity(1L);
            long id2 = factory.createEntity(1L);
            long id3 = factory.createEntity(1L);

            assertThat(id1).isNotEqualTo(id2);
            assertThat(id2).isNotEqualTo(id3);
        }

        @Test
        @DisplayName("should start entity IDs from 1")
        void shouldStartEntityIdsFrom1() {
            long entityId = factory.createEntity(1L);

            assertThat(entityId).isEqualTo(1L);
        }

        @Test
        @DisplayName("should use custom ID generator when provided")
        void shouldUseCustomIdGenerator() {
            AtomicLong customIdGen = new AtomicLong(100);
            factory = new DefaultEntityFactory(store, customIdGen);

            long entityId = factory.createEntity(1L);

            assertThat(entityId).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("createEntity(long matchId, List<BaseComponent> components, long[] values)")
    class CreateEntityWithComponents {

        private static class TestComponent extends BaseComponent {
            TestComponent(long id, String name) {
                super(id, name);
            }
        }

        @Test
        @DisplayName("should create entity and attach MATCH_ID plus provided components")
        void shouldCreateEntityWithMatchIdAndComponents() {
            long matchId = 42L;
            BaseComponent posX = new TestComponent(IdGeneratorV2.newId(), "POS_X");
            BaseComponent posY = new TestComponent(IdGeneratorV2.newId(), "POS_Y");
            List<BaseComponent> components = List.of(posX, posY);
            float[] values = {100f, 200f};

            long entityId = factory.createEntity(matchId, components, values);

            verify(store).createEntity(entityId);
            verify(store).attachComponent(entityId, CoreComponents.MATCH_ID, matchId);
            verify(store).attachComponents(entityId, components, values);
        }

        @Test
        @DisplayName("should attach ENTITY_ID component when creating with components")
        void shouldAttachEntityIdComponentWithComponents() {
            long matchId = 42L;
            BaseComponent posX = new TestComponent(IdGeneratorV2.newId(), "POS_X");
            List<BaseComponent> components = List.of(posX);
            float[] values = {100f};

            long entityId = factory.createEntity(matchId, components, values);

            verify(store).attachComponent(entityId, CoreComponents.ENTITY_ID, entityId);
        }

        @Test
        @DisplayName("should handle empty components list")
        void shouldHandleEmptyComponentsList() {
            long matchId = 42L;
            List<BaseComponent> components = List.of();
            float[] values = new float[0];

            long entityId = factory.createEntity(matchId, components, values);

            verify(store).createEntity(entityId);
            verify(store).attachComponent(entityId, CoreComponents.MATCH_ID, matchId);
            // Should not call attachComponents for empty list
        }

        @Test
        @DisplayName("should throw when components and values have different sizes")
        void shouldThrowWhenSizesMismatch() {
            long matchId = 42L;
            BaseComponent posX = new TestComponent(IdGeneratorV2.newId(), "POS_X");
            List<BaseComponent> components = List.of(posX);
            float[] values = {100f, 200f}; // Wrong size

            assertThatThrownBy(() -> factory.createEntity(matchId, components, values))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("size")
                    .hasMessageContaining("1")
                    .hasMessageContaining("2");
        }
    }

    @Nested
    @DisplayName("deleteEntity")
    class DeleteEntity {

        @Test
        @DisplayName("should delegate to store")
        void shouldDelegateToStore() {
            long entityId = 42L;

            factory.deleteEntity(entityId);

            verify(store).deleteEntity(entityId);
        }
    }

    @Nested
    @DisplayName("getMatchIdComponent")
    class GetMatchIdComponent {

        @Test
        @DisplayName("should return CoreComponents.MATCH_ID")
        void shouldReturnMatchIdComponent() {
            BaseComponent result = factory.getMatchIdComponent();

            assertThat(result).isSameAs(CoreComponents.MATCH_ID);
            assertThat(result.getName()).isEqualTo("MATCH_ID");
        }
    }
}
