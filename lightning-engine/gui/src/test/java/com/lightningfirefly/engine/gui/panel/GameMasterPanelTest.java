package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.TestComponentFactory;
import com.lightningfirefly.engine.gui.service.GameMasterService;
import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterInfo;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("GameMasterPanel")
class GameMasterPanelTest {

    @Mock
    private GameMasterService gameMasterService;

    private GameMasterPanel panel;
    private ComponentFactory factory;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        factory = new TestComponentFactory();
        when(gameMasterService.listGameMasters())
            .thenReturn(CompletableFuture.completedFuture(List.of()));
        panel = new GameMasterPanel(factory, 10, 20, 400, 500, gameMasterService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (panel != null) {
            panel.dispose();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("creates panel with correct dimensions")
        void createsPanel_withCorrectDimensions() {
            assertThat(panel.getX()).isEqualTo(10);
            assertThat(panel.getY()).isEqualTo(20);
            assertThat(panel.getWidth()).isEqualTo(400);
            assertThat(panel.getHeight()).isEqualTo(500);
        }

        @Test
        @DisplayName("panel is visible by default")
        void isVisible_byDefault() {
            assertThat(panel.isVisible()).isTrue();
        }

        @Test
        @DisplayName("returns the injected game master service")
        void returnsInjectedService() {
            assertThat(panel.getGameMasterService()).isSameAs(gameMasterService);
        }
    }

    @Nested
    @DisplayName("visibility")
    class Visibility {

        @Test
        @DisplayName("setVisible(false) hides the panel")
        void setVisibleFalse_hidesPanel() {
            panel.setVisible(false);
            assertThat(panel.isVisible()).isFalse();
        }

        @Test
        @DisplayName("setVisible(true) shows the panel")
        void setVisibleTrue_showsPanel() {
            panel.setVisible(false);
            panel.setVisible(true);
            assertThat(panel.isVisible()).isTrue();
        }
    }

    @Nested
    @DisplayName("refreshGameMasters")
    class RefreshGameMasters {

        @Test
        @DisplayName("calls game master service listGameMasters")
        void callsServiceListGameMasters() {
            panel.refreshGameMasters();

            verify(gameMasterService, atLeast(1)).listGameMasters();
        }

        @Test
        @DisplayName("updates game masters list when service returns data")
        void updatesGameMastersList() throws Exception {
            List<GameMasterInfo> mockGameMasters = List.of(
                new GameMasterInfo("TickCounter", 0),
                new GameMasterInfo("AIController", 2)
            );
            when(gameMasterService.listGameMasters())
                .thenReturn(CompletableFuture.completedFuture(mockGameMasters));

            panel.refreshGameMasters();

            // Wait for async operation
            Thread.sleep(100);
            panel.update();

            assertThat(panel.getGameMasters()).hasSize(2);
            assertThat(panel.getGameMasters().get(0).name()).isEqualTo("TickCounter");
            assertThat(panel.getGameMasters().get(1).name()).isEqualTo("AIController");
        }
    }

    @Nested
    @DisplayName("getGameMasters")
    class GetGameMasters {

        @Test
        @DisplayName("returns empty list initially")
        void returnsEmptyList_initially() throws Exception {
            Thread.sleep(100);
            panel.update();
            assertThat(panel.getGameMasters()).isEmpty();
        }

        @Test
        @DisplayName("returns a copy of the list")
        void returnsCopyOfList() throws Exception {
            List<GameMasterInfo> mockGameMasters = List.of(
                new GameMasterInfo("TestGM", 1)
            );
            when(gameMasterService.listGameMasters())
                .thenReturn(CompletableFuture.completedFuture(mockGameMasters));

            panel.refreshGameMasters();
            Thread.sleep(100);
            panel.update();

            var list1 = panel.getGameMasters();
            var list2 = panel.getGameMasters();

            assertThat(list1).isNotSameAs(list2);
            assertThat(list1).isEqualTo(list2);
        }
    }

    @Nested
    @DisplayName("test helpers")
    class TestHelpers {

        @Test
        @DisplayName("setJarPath and getJarPath work correctly")
        void jarPathFieldWorks() {
            panel.setJarPath("/path/to/game-master.jar");
            assertThat(panel.getJarPath()).isEqualTo("/path/to/game-master.jar");
        }

        @Test
        @DisplayName("selectGameMaster and getSelectedGameMasterIndex work")
        void selectGameMasterWorks() throws Exception {
            // First populate the list with some game masters
            List<GameMasterInfo> mockGameMasters = List.of(
                new GameMasterInfo("GM1", 0),
                new GameMasterInfo("GM2", 0),
                new GameMasterInfo("GM3", 0)
            );
            when(gameMasterService.listGameMasters())
                .thenReturn(CompletableFuture.completedFuture(mockGameMasters));

            panel.refreshGameMasters();
            Thread.sleep(100);
            panel.update();

            panel.selectGameMaster(2);
            assertThat(panel.getSelectedGameMasterIndex()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        @Test
        @DisplayName("status message is set after refresh")
        void statusMessageIsSet_afterRefresh() throws Exception {
            List<GameMasterInfo> mockGameMasters = List.of(
                new GameMasterInfo("TestGM", 0)
            );
            when(gameMasterService.listGameMasters())
                .thenReturn(CompletableFuture.completedFuture(mockGameMasters));

            panel.refreshGameMasters();
            Thread.sleep(100);
            panel.update();

            assertThat(panel.getStatusMessage()).contains("Loaded");
        }
    }

    @Nested
    @DisplayName("dispose")
    class Dispose {

        @Test
        @DisplayName("can be called multiple times without error")
        void canBeCalledMultipleTimes() {
            panel.dispose();
            panel.dispose();
            // No exception = success
        }

        @Test
        @DisplayName("shuts down the game master service")
        void shutsDownService() {
            panel.dispose();
            verify(gameMasterService).shutdown();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("can be called multiple times without error")
        void canBeCalledMultipleTimes() {
            panel.update();
            panel.update();
            panel.update();
            // No exception = success
        }
    }
}
