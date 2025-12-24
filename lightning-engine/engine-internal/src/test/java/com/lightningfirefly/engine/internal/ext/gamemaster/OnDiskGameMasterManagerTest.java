package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.engine.core.entity.EntityFactory;
import com.lightningfirefly.engine.core.resources.ResourceManager;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.game.domain.GameMaster;
import com.lightningfirefly.game.domain.GameMasterContext;
import com.lightningfirefly.game.backend.installation.GameMasterFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OnDiskGameMasterManager")
class OnDiskGameMasterManagerTest {

    @TempDir
    Path tempDir;

    private OnDiskGameMasterManager manager;
    private ModuleContext mockContext;
    private com.lightningfirefly.engine.core.command.CommandExecutor mockCommandExecutor;
    private ResourceManager mockResourceManager;

    @BeforeEach
    void setUp() {
        mockContext = mock(ModuleContext.class);
        when(mockContext.getEntityComponentStore()).thenReturn(mock(EntityComponentStore.class));
        when(mockContext.getEntityFactory()).thenReturn(mock(EntityFactory.class));
        mockCommandExecutor = mock(com.lightningfirefly.engine.core.command.CommandExecutor.class);
        mockResourceManager = mock(ResourceManager.class);

        manager = new OnDiskGameMasterManager(
                tempDir,
                new GameMasterFactoryFileLoader(),
                mockContext,
                mockCommandExecutor,
                mockResourceManager
        );
    }

    @Nested
    @DisplayName("reloadInstalled")
    class ReloadInstalled {

        @Test
        @DisplayName("should create directory if it doesn't exist")
        void shouldCreateDirectoryIfItDoesntExist() throws IOException {
            Path nonExistentDir = tempDir.resolve("new-game-masters-dir");
            OnDiskGameMasterManager mgr = new OnDiskGameMasterManager(
                    nonExistentDir,
                    new GameMasterFactoryFileLoader(),
                    mockContext,
                    mockCommandExecutor,
                    mockResourceManager
            );

            mgr.reloadInstalled();

            assertThat(Files.exists(nonExistentDir)).isTrue();
        }

        @Test
        @DisplayName("should return empty list when directory is empty")
        void shouldReturnEmptyListWhenDirectoryIsEmpty() throws IOException {
            manager.reloadInstalled();

            assertThat(manager.getAvailableGameMasters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("installGameMaster from class")
    class InstallFromClass {

        @Test
        @DisplayName("should install game master from factory class")
        void shouldInstallFromFactoryClass() {
            manager.installGameMaster(TestGameMasterFactory.class);

            assertThat(manager.hasGameMaster("TestGameMaster")).isTrue();
            assertThat(manager.getAvailableGameMasters()).contains("TestGameMaster");
        }
    }

    @Nested
    @DisplayName("installGameMaster from file")
    class InstallFromFile {

        @Test
        @DisplayName("should throw IOException when file doesn't exist")
        void shouldThrowWhenFileDoesntExist() {
            Path nonExistentFile = tempDir.resolve("nonexistent.jar");

            assertThatThrownBy(() -> manager.installGameMaster(nonExistentFile))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("should throw IOException when file is not a JAR")
        void shouldThrowWhenNotJar() throws IOException {
            Path textFile = tempDir.resolve("notajar.txt");
            Files.writeString(textFile, "not a jar");

            assertThatThrownBy(() -> manager.installGameMaster(textFile))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("not a JAR file");
        }
    }

    @Nested
    @DisplayName("createForMatch")
    class CreateForMatch {

        @Test
        @DisplayName("should return null when game master not found")
        void shouldReturnNullWhenNotFound() {
            GameMaster result = manager.createForMatch("NonExistent", 1L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should create game master for match")
        void shouldCreateGameMasterForMatch() {
            manager.installGameMaster(TestGameMasterFactory.class);

            GameMaster result = manager.createForMatch("TestGameMaster", 42L);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(TestGameMaster.class);
        }
    }

    @Nested
    @DisplayName("uninstallGameMaster")
    class UninstallGameMaster {

        @Test
        @DisplayName("should return false when game master not found")
        void shouldReturnFalseWhenNotFound() {
            boolean result = manager.uninstallGameMaster("NonExistent");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true and remove game master when found")
        void shouldReturnTrueAndRemoveWhenFound() {
            manager.installGameMaster(TestGameMasterFactory.class);
            assertThat(manager.hasGameMaster("TestGameMaster")).isTrue();

            boolean result = manager.uninstallGameMaster("TestGameMaster");

            assertThat(result).isTrue();
            assertThat(manager.hasGameMaster("TestGameMaster")).isFalse();
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("should clear all cached game masters")
        void shouldClearAllCachedGameMasters() {
            manager.installGameMaster(TestGameMasterFactory.class);
            assertThat(manager.getAvailableGameMasters()).isNotEmpty();

            manager.reset();

            assertThat(manager.getAvailableGameMasters()).isEmpty();
        }
    }

    // Test implementations
    public static class TestGameMasterFactory implements GameMasterFactory {
        @Override
        public GameMaster create(GameMasterContext context) {
            return new TestGameMaster(context);
        }

        @Override
        public String getName() {
            return "TestGameMaster";
        }
    }

    public static class TestGameMaster implements GameMaster {
        private final GameMasterContext context;
        private int tickCount = 0;

        public TestGameMaster(GameMasterContext context) {
            this.context = context;
        }

        @Override
        public void onTick() {
            tickCount++;
        }

        public int getTickCount() {
            return tickCount;
        }
    }
}
