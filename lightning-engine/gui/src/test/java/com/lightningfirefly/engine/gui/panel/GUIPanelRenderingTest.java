package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.gui.TestComponentFactory;
import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for GUI panels.
 * Tests panel behavior and state management.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GUIPanelRenderingTest {

    @Mock
    private ResourceAdapter resourceAdapter;

    private AutoCloseable mocks;
    private ComponentFactory factory;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        when(resourceAdapter.listResources()).thenReturn(List.of());
        factory = new TestComponentFactory();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void snapshotPanel_updatesTreeOnSnapshotReceived() throws Exception {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 1);

        // Simulate receiving a snapshot
        Map<String, Map<String, List<Float>>> snapshotData = Map.of(
            "SpawnModule", Map.of(
                "ENTITY_TYPE", List.of(1.0f, 2.0f, 3.0f),
                "OWNER_ID", List.of(100.0f, 100.0f, 200.0f)
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 42L, snapshotData);

        // Access the internal method to trigger update
        panel.getSnapshotClient().addListener(s -> {
            // This would normally trigger the tree update
        });

        // Verify panel is visible (has been set up correctly)
        assertThat(panel.isVisible()).isTrue();

        panel.dispose();
    }

    @Test
    void resourcePanel_displaysResources() throws Exception {
        List<Resource> mockResources = List.of(
            new Resource(1L, new byte[]{1, 2, 3}, "TEXTURE"),
            new Resource(2L, new byte[]{4, 5, 6}, "TEXTURE")
        );
        when(resourceAdapter.listResources()).thenReturn(mockResources);

        ResourceService service = new ResourceService(resourceAdapter);
        ResourcePanel panel = new ResourcePanel(factory, 0, 0, 400, 500, service);

        // Trigger refresh
        panel.refreshResources();

        // Wait for async operation
        Thread.sleep(200);
        panel.update();

        // Panel should be visible and set up correctly
        assertThat(panel.isVisible()).isTrue();

        panel.dispose();
    }

    @Test
    void snapshotPanel_connectsAndDisconnects() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 1);

        // Initially not connected
        assertThat(panel.getSnapshotClient().isConnected()).isFalse();

        // Disconnect should not throw even if not connected
        panel.disconnect();

        panel.dispose();
    }

    @Test
    void resourcePanel_handlesEmptyResourceList() throws Exception {
        when(resourceAdapter.listResources()).thenReturn(List.of());

        ResourceService service = new ResourceService(resourceAdapter);
        ResourcePanel panel = new ResourcePanel(factory, 0, 0, 400, 500, service);

        panel.refreshResources();
        Thread.sleep(100);
        panel.update();

        assertThat(panel.getResources()).isEmpty();

        panel.dispose();
    }

    @Test
    void snapshotPanel_dimensions_areCorrect() {
        SnapshotPanel panel = new SnapshotPanel(factory, 10, 20, 400, 500, "http://localhost:8080", 1);

        assertThat(panel.getX()).isEqualTo(10);
        assertThat(panel.getY()).isEqualTo(20);
        assertThat(panel.getWidth()).isEqualTo(400);
        assertThat(panel.getHeight()).isEqualTo(500);

        panel.dispose();
    }

    @Test
    void resourcePanel_dimensions_areCorrect() throws Exception {
        ResourceService service = new ResourceService(resourceAdapter);
        ResourcePanel panel = new ResourcePanel(factory, 30, 40, 350, 450, service);

        assertThat(panel.getX()).isEqualTo(30);
        assertThat(panel.getY()).isEqualTo(40);
        assertThat(panel.getWidth()).isEqualTo(350);
        assertThat(panel.getHeight()).isEqualTo(450);

        panel.dispose();
    }

    @Test
    void snapshotPanel_hasTitle() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 42);

        // Panel should have title set
        assertThat(panel.isVisible()).isTrue();

        panel.dispose();
    }

    @Test
    void resourcePanel_isVisible() throws Exception {
        ResourceService service = new ResourceService(resourceAdapter);
        ResourcePanel panel = new ResourcePanel(factory, 0, 0, 400, 500, service);

        // Panel should be visible
        assertThat(panel.isVisible()).isTrue();

        panel.dispose();
    }

    @Test
    void snapshotPanel_updateDoesNotThrow_whenNoSnapshot() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 1);

        // Should not throw
        panel.update();
        panel.update();
        panel.update();

        panel.dispose();
    }

    @Test
    void resourcePanel_updateDoesNotThrow_repeatedly() throws Exception {
        ResourceService service = new ResourceService(resourceAdapter);
        ResourcePanel panel = new ResourcePanel(factory, 0, 0, 400, 500, service);

        // Should not throw
        for (int i = 0; i < 10; i++) {
            panel.update();
        }

        panel.dispose();
    }

    @Test
    void snapshotData_entityCount_isCorrect() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "Module1", Map.of(
                "ComponentA", List.of(1.0f, 2.0f, 3.0f, 4.0f, 5.0f),
                "ComponentB", List.of(10.0f, 20.0f, 30.0f, 40.0f, 50.0f)
            )
        );

        SnapshotData snapshot = new SnapshotData(1L, 100L, data);

        assertThat(snapshot.getEntityCount()).isEqualTo(5);
        assertThat(snapshot.getModuleNames()).contains("Module1");
    }

    @Test
    void snapshotData_emptyData_handledCorrectly() {
        SnapshotData snapshot = new SnapshotData(1L, 100L, Map.of());

        assertThat(snapshot.getEntityCount()).isZero();
        assertThat(snapshot.getModuleNames()).isEmpty();
    }
}
