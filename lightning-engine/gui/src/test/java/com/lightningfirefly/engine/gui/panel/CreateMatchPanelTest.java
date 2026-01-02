package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.TestComponentFactory;
import com.lightningfirefly.engine.gui.panel.CreateMatchPanel.PanelBounds;
import com.lightningfirefly.engine.gui.service.GameMasterService;
import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterInfo;
import com.lightningfirefly.engine.gui.service.MatchService;
import com.lightningfirefly.engine.gui.service.MatchService.CreateMatchRequest;
import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateMatchPanel.
 */
@DisplayName("CreateMatchPanel")
class CreateMatchPanelTest {

    @Mock
    private MatchService mockMatchService;

    @Mock
    private ModuleService mockModuleService;

    @Mock
    private GameMasterService mockGameMasterService;

    private ComponentFactory factory;
    private CreateMatchPanel panel;
    private AutoCloseable mocks;
    private static final PanelBounds BOUNDS = new PanelBounds(0, 0, 600, 400);

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        factory = new TestComponentFactory();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Nested
    @DisplayName("Module and Game Master Loading")
    class ModuleAndGameMasterLoading {

        @Test
        @DisplayName("refresh loads modules and game masters")
        void refresh_loadsModulesAndGameMasters() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of(
                new ModuleInfo("MoveModule", "move", 0),
                new ModuleInfo("EntityModule", null, 1)
            )));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of(
                new GameMasterInfo("TickCounter", 0)
            )));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);

            // When
            panel.refresh();
            panel.update();

            // Then
            assertThat(panel.getAvailableModules()).hasSize(2);
            assertThat(panel.getAvailableGameMasters()).hasSize(1);
        }

        @Test
        @DisplayName("refresh clears previous selections")
        void refresh_clearsPreviousSelections() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of(
                new ModuleInfo("MoveModule", "move", 0)
            )));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of(
                new GameMasterInfo("TickCounter", 0)
            )));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);
            panel.refresh();
            panel.update();

            // Select something
            panel.selectModule(0);

            // When
            panel.refresh();

            // Then
            assertThat(panel.getSelectedModules()).isEmpty();
            assertThat(panel.getSelectedGameMasters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Match Creation")
    class MatchCreation {

        @Test
        @DisplayName("triggerCreate calls matchService.createMatch with selected items")
        void triggerCreate_callsMatchServiceWithSelectedItems() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of(
                new ModuleInfo("MoveModule", "move", 0),
                new ModuleInfo("EntityModule", null, 1)
            )));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of(
                new GameMasterInfo("TickCounter", 0),
                new GameMasterInfo("AIController", 0)
            )));
            when(mockMatchService.createMatch(any(CreateMatchRequest.class)))
                .thenReturn(completedFuture(42L));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);
            panel.refresh();
            panel.update();

            // Select items
            panel.selectModule(0); // MoveModule
            panel.selectGameMaster(1); // AIController

            // When
            panel.triggerCreate();

            // Then
            ArgumentCaptor<CreateMatchRequest> captor = ArgumentCaptor.forClass(CreateMatchRequest.class);
            verify(mockMatchService).createMatch(captor.capture());

            CreateMatchRequest request = captor.getValue();
            assertThat(request.enabledModules()).contains("MoveModule");
            assertThat(request.enabledGameMasters()).contains("AIController");
        }

        @Test
        @DisplayName("successful creation triggers onMatchCreated callback")
        void successfulCreation_triggersCallback() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of()));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of()));
            when(mockMatchService.createMatch(any(CreateMatchRequest.class)))
                .thenReturn(completedFuture(123L));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);

            AtomicLong createdMatchId = new AtomicLong(-1);
            panel.setOnMatchCreated(createdMatchId::set);

            // When
            panel.triggerCreate();

            // Then
            assertThat(createdMatchId.get()).isEqualTo(123L);
        }

        @Test
        @DisplayName("failed creation does not trigger onMatchCreated callback")
        void failedCreation_doesNotTriggerCallback() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of()));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of()));
            when(mockMatchService.createMatch(any(CreateMatchRequest.class)))
                .thenReturn(completedFuture(-1L)); // Failure

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);

            AtomicBoolean callbackCalled = new AtomicBoolean(false);
            panel.setOnMatchCreated(id -> callbackCalled.set(true));

            // When
            panel.triggerCreate();

            // Then
            assertThat(callbackCalled.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("Cancel Action")
    class CancelAction {

        @Test
        @DisplayName("triggerCancel calls onCancel callback")
        void triggerCancel_callsCallback() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of()));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of()));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);

            AtomicBoolean cancelCalled = new AtomicBoolean(false);
            panel.setOnCancel(() -> cancelCalled.set(true));

            // When
            panel.triggerCancel();

            // Then
            assertThat(cancelCalled.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("Selection")
    class Selection {

        @Test
        @DisplayName("selectModule adds module to selection")
        void selectModule_addsToSelection() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of(
                new ModuleInfo("MoveModule", "move", 0),
                new ModuleInfo("EntityModule", null, 1)
            )));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of()));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);
            panel.refresh();
            panel.update();

            // When
            panel.selectModule(0);
            panel.selectModule(1);

            // Then
            assertThat(panel.getSelectedModules()).containsExactlyInAnyOrder("MoveModule", "EntityModule");
        }

        @Test
        @DisplayName("selectGameMaster adds game master to selection")
        void selectGameMaster_addsToSelection() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of()));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of(
                new GameMasterInfo("TickCounter", 0),
                new GameMasterInfo("AIController", 0)
            )));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);
            panel.refresh();
            panel.update();

            // When
            panel.selectGameMaster(0);

            // Then
            assertThat(panel.getSelectedGameMasters()).containsExactly("TickCounter");
        }

        @Test
        @DisplayName("clearSelections removes all selections")
        void clearSelections_removesAllSelections() {
            // Given
            when(mockModuleService.listModules()).thenReturn(completedFuture(List.of(
                new ModuleInfo("MoveModule", "move", 0)
            )));
            when(mockGameMasterService.listGameMasters()).thenReturn(completedFuture(List.of(
                new GameMasterInfo("TickCounter", 0)
            )));

            panel = new CreateMatchPanel(factory, BOUNDS, mockMatchService, mockModuleService, mockGameMasterService);
            panel.refresh();
            panel.update();
            panel.selectModule(0);
            panel.selectGameMaster(0);

            // When
            panel.clearSelections();

            // Then
            assertThat(panel.getSelectedModules()).isEmpty();
            assertThat(panel.getSelectedGameMasters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("PanelBounds DTO")
    class PanelBoundsDTO {

        @Test
        @DisplayName("PanelBounds stores coordinates correctly")
        void panelBounds_storesCoordinatesCorrectly() {
            // Given/When
            PanelBounds bounds = new PanelBounds(10, 20, 300, 400);

            // Then
            assertThat(bounds.x()).isEqualTo(10);
            assertThat(bounds.y()).isEqualTo(20);
            assertThat(bounds.width()).isEqualTo(300);
            assertThat(bounds.height()).isEqualTo(400);
        }
    }
}
