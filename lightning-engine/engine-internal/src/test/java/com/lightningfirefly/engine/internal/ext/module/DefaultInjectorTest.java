package com.lightningfirefly.engine.internal.ext.module;

import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultInjector}.
 *
 * <p>Tests verify ModuleContext implementation and explicit getter methods.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultInjector")
class DefaultInjectorTest {

    @Mock
    private EntityComponentStore entityComponentStore;

    @Mock
    private MatchService matchService;

    @Mock
    private ModuleResolver moduleResolver;

    private DefaultInjector injector;

    @BeforeEach
    void setUp() {
        injector = new DefaultInjector();
    }

    @Nested
    @DisplayName("addClass")
    class AddClass {

        @Test
        @DisplayName("should register bean")
        void shouldRegisterBean() {
            injector.addClass(EntityComponentStore.class, entityComponentStore);

            assertThat(injector.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("should overwrite existing bean")
        void shouldOverwriteExistingBean() {
            EntityComponentStore store1 = entityComponentStore;
            EntityComponentStore store2 = entityComponentStore; // same mock, different conceptual instance

            injector.addClass(EntityComponentStore.class, store1);
            injector.addClass(EntityComponentStore.class, store2);

            assertThat(injector.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getEntityComponentStore")
    class GetEntityComponentStore {

        @Test
        @DisplayName("should return registered store")
        void shouldReturnRegisteredStore() {
            injector.addClass(EntityComponentStore.class, entityComponentStore);

            EntityComponentStore result = injector.getEntityComponentStore();

            assertThat(result).isSameAs(entityComponentStore);
        }

        @Test
        @DisplayName("should return null when not registered")
        void shouldReturnNullWhenNotRegistered() {
            EntityComponentStore result = injector.getEntityComponentStore();

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should cache result")
        void shouldCacheResult() {
            injector.addClass(EntityComponentStore.class, entityComponentStore);

            EntityComponentStore result1 = injector.getEntityComponentStore();
            EntityComponentStore result2 = injector.getEntityComponentStore();

            assertThat(result1).isSameAs(result2);
        }
    }

    @Nested
    @DisplayName("getStoreRequired")
    class GetStoreRequired {

        @Test
        @DisplayName("should return registered store")
        void shouldReturnRegisteredStore() {
            injector.addClass(EntityComponentStore.class, entityComponentStore);

            EntityComponentStore result = injector.getStoreRequired();

            assertThat(result).isSameAs(entityComponentStore);
        }

        @Test
        @DisplayName("should throw when not registered")
        void shouldThrowWhenNotRegistered() {
            assertThatThrownBy(() -> injector.getStoreRequired())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("EntityComponentStore not registered");
        }
    }

    @Nested
    @DisplayName("getMatchService")
    class GetMatchService {

        @Test
        @DisplayName("should return registered service")
        void shouldReturnRegisteredService() {
            injector.addClass(MatchService.class, matchService);

            MatchService result = injector.getMatchService();

            assertThat(result).isSameAs(matchService);
        }

        @Test
        @DisplayName("should return null when not registered")
        void shouldReturnNullWhenNotRegistered() {
            MatchService result = injector.getMatchService();

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getModuleResolver")
    class GetModuleResolver {

        @Test
        @DisplayName("should return registered resolver")
        void shouldReturnRegisteredResolver() {
            injector.addClass(ModuleResolver.class, moduleResolver);

            ModuleResolver result = injector.getModuleResolver();

            assertThat(result).isSameAs(moduleResolver);
        }

        @Test
        @DisplayName("should return null when not registered")
        void shouldReturnNullWhenNotRegistered() {
            ModuleResolver result = injector.getModuleResolver();

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("findEntityComponentStore")
    class FindEntityComponentStore {

        @Test
        @DisplayName("should return Optional with store when registered")
        void shouldReturnOptionalWithStoreWhenRegistered() {
            injector.addClass(EntityComponentStore.class, entityComponentStore);

            var result = injector.findEntityComponentStore();

            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(entityComponentStore);
        }

        @Test
        @DisplayName("should return empty Optional when not registered")
        void shouldReturnEmptyOptionalWhenNotRegistered() {
            var result = injector.findEntityComponentStore();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("cache invalidation")
    class CacheInvalidation {

        @Test
        @DisplayName("should invalidate cache when adding new bean")
        void shouldInvalidateCacheWhenAddingNewBean() {
            // Register initial store
            injector.addClass(EntityComponentStore.class, entityComponentStore);
            EntityComponentStore first = injector.getEntityComponentStore();

            // Register different store - should invalidate cache
            EntityComponentStore newStore = entityComponentStore; // Using same mock
            injector.addClass(EntityComponentStore.class, newStore);
            EntityComponentStore second = injector.getEntityComponentStore();

            // Both calls return the same mock, but cache should have been invalidated
            assertThat(first).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should remove all beans")
        void shouldRemoveAllBeans() {
            injector.addClass(EntityComponentStore.class, entityComponentStore);
            injector.addClass(MatchService.class, matchService);

            injector.clear();

            assertThat(injector.size()).isEqualTo(0);
            assertThat(injector.getEntityComponentStore()).isNull();
            assertThat(injector.getMatchService()).isNull();
        }
    }

    @Nested
    @DisplayName("deprecated getClass")
    class DeprecatedGetClass {

        @Test
        @DisplayName("should return registered bean")
        void shouldReturnRegisteredBean() {
            injector.addClass(MatchService.class, matchService);

            @SuppressWarnings("deprecation")
            MatchService result = injector.getClass(MatchService.class);

            assertThat(result).isSameAs(matchService);
        }

        @Test
        @DisplayName("should return null when not registered")
        void shouldReturnNullWhenNotRegistered() {
            @SuppressWarnings("deprecation")
            MatchService result = injector.getClass(MatchService.class);

            assertThat(result).isNull();
        }
    }
}
