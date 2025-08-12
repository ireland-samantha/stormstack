package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.gui.TestComponentFactory;
import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResourcePanelTest {

    @Mock
    private ResourceAdapter resourceAdapter;

    private ResourceService resourceService;
    private ResourcePanel panel;
    private AutoCloseable mocks;
    private ComponentFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        when(resourceAdapter.listResources()).thenReturn(List.of());
        resourceService = new ResourceService(resourceAdapter);
        factory = new TestComponentFactory();
        panel = new ResourcePanel(factory, 10, 20, 400, 500, resourceService);
    }

    @AfterEach
    void tearDown() throws Exception {
        panel.dispose();
        mocks.close();
    }

    @Test
    void constructor_createsPanel_withCorrectDimensions() {
        assertThat(panel.getX()).isEqualTo(10);
        assertThat(panel.getY()).isEqualTo(20);
        assertThat(panel.getWidth()).isEqualTo(400);
        assertThat(panel.getHeight()).isEqualTo(500);
    }

    @Test
    void getResourceService_returnsNonNull() {
        assertThat(panel.getResourceService()).isNotNull();
    }

    @Test
    void getResources_returnsEmptyList_initially() {
        // Wait a bit for async operation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        panel.update();

        assertThat(panel.getResources()).isEmpty();
    }

    @Test
    void isVisible_returnsTrue_byDefault() {
        assertThat(panel.isVisible()).isTrue();
    }

    @Test
    void setVisible_changesVisibility() {
        panel.setVisible(false);
        assertThat(panel.isVisible()).isFalse();

        panel.setVisible(true);
        assertThat(panel.isVisible()).isTrue();
    }

    @Test
    void panelIsSetUpCorrectly() {
        // Panel should be visible and set up correctly (composition pattern)
        assertThat(panel.isVisible()).isTrue();
        assertThat(panel.getWidth()).isEqualTo(400);
        assertThat(panel.getHeight()).isEqualTo(500);
    }

    @Test
    void refreshResources_callsAdapter() throws Exception {
        List<Resource> mockResources = List.of(
            new Resource(1L, new byte[]{1, 2}, "TEXTURE"),
            new Resource(2L, new byte[]{3, 4}, "TEXTURE")
        );
        when(resourceAdapter.listResources()).thenReturn(mockResources);

        panel.refreshResources();

        // Wait for async operation
        Thread.sleep(200);
        panel.update();

        verify(resourceAdapter, atLeast(1)).listResources();
    }

    @Test
    void dispose_canBeCalledMultipleTimes() {
        // Should not throw
        panel.dispose();
        panel.dispose();
    }

    @Test
    void update_handlesNullSnapshot() {
        // Should not throw
        panel.update();
        panel.update();
    }
}
