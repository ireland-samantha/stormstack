package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.TestComponentFactory;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import com.lightningfirefly.engine.rendering.render2d.TreeNode;
import com.lightningfirefly.engine.rendering.render2d.TreeView;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class SnapshotPanelTest {

    private ComponentFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TestComponentFactory();
    }

    @Test
    void constructor_createsPanel_withCorrectDimensions() {
        SnapshotPanel panel = new SnapshotPanel(factory, 10, 20, 400, 500, "http://localhost:8080", 1);

        assertThat(panel.getX()).isEqualTo(10);
        assertThat(panel.getY()).isEqualTo(20);
        assertThat(panel.getWidth()).isEqualTo(400);
        assertThat(panel.getHeight()).isEqualTo(500);
    }

    @Test
    void getSnapshotClient_returnsNonNull() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 42);

        SnapshotWebSocketClient client = panel.getSnapshotClient();

        assertThat(client).isNotNull();
    }

    @Test
    void getLatestSnapshot_returnsNull_initially() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 1);

        assertThat(panel.getLatestSnapshot()).isNull();
    }

    @Test
    void isVisible_returnsTrue_byDefault() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 1);

        assertThat(panel.isVisible()).isTrue();
    }

    @Test
    void setVisible_changesVisibility() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 1);

        panel.setVisible(false);
        assertThat(panel.isVisible()).isFalse();

        panel.setVisible(true);
        assertThat(panel.isVisible()).isTrue();
    }

    @Test
    void dispose_canBeCalledMultipleTimes() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 500, "http://localhost:8080", 1);

        // Should not throw
        panel.dispose();
        panel.dispose();
    }

    @Test
    void setSnapshotData_createsTreeWithMatchAndModules() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshot();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        assertThat(tree.getRootNodes()).hasSize(1);

        // Root is "Matches"
        TreeNode rootNode = tree.getRootNodes().get(0);
        assertThat(rootNode.getLabel()).isEqualTo("Matches");

        // First child is the match node (e.g., "Match 1 (Tick: 42)")
        assertThat(rootNode.getChildren()).hasSize(1);
        TreeNode matchNode = rootNode.getChildren().get(0);
        assertThat(matchNode.getLabel()).contains("Match");
        assertThat(matchNode.getLabel()).contains("Tick: 42");

        // Check module names under the match
        List<String> moduleNames = matchNode.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();
        assertThat(moduleNames).containsExactlyInAnyOrder("SpawnModule", "MoveModule");
    }

    @Test
    void setSnapshotData_createsEntityNodesWithCorrectNames() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshot();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        TreeNode matchNode = rootNode.getChildren().get(0);

        // Find SpawnModule
        TreeNode spawnModule = matchNode.getChildren().stream()
            .filter(n -> n.getLabel().equals("SpawnModule"))
            .findFirst()
            .orElseThrow();

        // Verify entity node names (using ENTITY_ID values from test data)
        List<String> entityNames = spawnModule.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();
        assertThat(entityNames).containsExactly("Entity 101", "Entity 102", "Entity 103");
    }

    @Test
    void setSnapshotData_entityNodesHaveNoChildrenInTree() {
        // In the new design, entity nodes don't have component children in the tree.
        // Components are shown in the detail panel when an entity is selected.
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshot();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        TreeNode matchNode = rootNode.getChildren().get(0);

        // Find SpawnModule
        TreeNode spawnModule = matchNode.getChildren().stream()
            .filter(n -> n.getLabel().equals("SpawnModule"))
            .findFirst()
            .orElseThrow();

        // Get first entity (using ENTITY_ID from test data)
        TreeNode entity0 = spawnModule.getChildren().get(0);
        assertThat(entity0.getLabel()).isEqualTo("Entity 101");

        // Entity nodes should NOT have children in the new design
        // (components are shown in the detail panel)
        assertThat(entity0.getChildren()).isEmpty();
    }

    @Test
    void detailPanel_exists() {
        // Verify that the detail panel and component list exist
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);

        assertThat(panel.getDetailPanel()).as("Detail panel should exist").isNotNull();
        assertThat(panel.getComponentList()).as("Component list should exist").isNotNull();
    }

    @Test
    void setSnapshotData_treeContainsEntityNodes() {
        // Verify entity nodes exist in the tree structure
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);

        Map<String, Map<String, List<Float>>> data = new HashMap<>();
        Map<String, List<Float>> moduleData = new HashMap<>();
        moduleData.put("VALUE", List.of(Float.NaN));
        data.put("TestModule", moduleData);

        SnapshotData snapshot = new SnapshotData(1L, 1L, data);
        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        assertThat(rootNode.getLabel()).isEqualTo("Matches");

        TreeNode matchNode = rootNode.getChildren().get(0);
        assertThat(matchNode.getChildren()).hasSize(1); // TestModule

        TreeNode moduleNode = matchNode.getChildren().get(0);
        assertThat(moduleNode.getLabel()).isEqualTo("TestModule");
        assertThat(moduleNode.getChildren()).hasSize(1); // Entity 0

        TreeNode entityNode = moduleNode.getChildren().get(0);
        // Without ENTITY_ID in this snapshot, shows fallback format
        assertThat(entityNode.getLabel()).isEqualTo("Entity (idx: 0)");
    }

    @Test
    void setSnapshotData_preservesModuleExpansionState() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshot();

        // Set initial snapshot
        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        TreeNode matchNode = rootNode.getChildren().get(0);
        TreeNode spawnModule = matchNode.getChildren().stream()
            .filter(n -> n.getLabel().equals("SpawnModule"))
            .findFirst()
            .orElseThrow();

        // Collapse SpawnModule
        spawnModule.setExpanded(false);

        // Update with new snapshot (same structure)
        panel.setSnapshotData(createTestSnapshot());

        // Verify expansion state is preserved for modules
        tree = panel.getEntityTree();
        rootNode = tree.getRootNodes().get(0);
        matchNode = rootNode.getChildren().get(0);
        spawnModule = matchNode.getChildren().stream()
            .filter(n -> n.getLabel().equals("SpawnModule"))
            .findFirst()
            .orElseThrow();

        assertThat(spawnModule.isExpanded()).isFalse();
    }

    @Test
    void setSnapshotData_handlesEmptySnapshot() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData emptySnapshot = new SnapshotData(1L, 0L, new HashMap<>());

        panel.setSnapshotData(emptySnapshot);

        TreeView tree = panel.getEntityTree();
        assertThat(tree.getRootNodes()).hasSize(1);
        // With no data, tree shows "Matches" with no children, or "No snapshots"
        TreeNode rootNode = tree.getRootNodes().get(0);
        // Empty snapshots result in "Matches" root with no match nodes since data is empty
        assertThat(rootNode.getLabel()).isEqualTo("Matches");
    }

    @Test
    void setSnapshotData_multipleEntitiesShowInTree() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshot();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        TreeNode matchNode = rootNode.getChildren().get(0);
        TreeNode spawnModule = matchNode.getChildren().stream()
            .filter(n -> n.getLabel().equals("SpawnModule"))
            .findFirst()
            .orElseThrow();

        // Check all entities are listed (using ENTITY_ID from test data)
        assertThat(spawnModule.getChildren()).hasSize(3);
        assertThat(spawnModule.getChildren().get(0).getLabel()).isEqualTo("Entity 101");
        assertThat(spawnModule.getChildren().get(1).getLabel()).isEqualTo("Entity 102");
        assertThat(spawnModule.getChildren().get(2).getLabel()).isEqualTo("Entity 103");

        // Entity nodes should have no children (components shown in detail panel)
        for (TreeNode entityNode : spawnModule.getChildren()) {
            assertThat(entityNode.getChildren()).isEmpty();
        }
    }

    @Test
    void setSnapshotData_moduleNodesAreExpandedByDefault() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshot();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        TreeNode matchNode = rootNode.getChildren().get(0);

        // Check that module nodes are expanded by default
        for (TreeNode moduleNode : matchNode.getChildren()) {
            log.info("Module: " + moduleNode.getLabel() + " (expanded=" + moduleNode.isExpanded() + ")");

            // Module nodes should be expanded by default to show entities
            assertThat(moduleNode.isExpanded())
                .as("Module node '%s' should be expanded by default", moduleNode.getLabel())
                .isTrue();

            // Verify entity children exist
            assertThat(moduleNode.getChildren())
                .as("Module '%s' should have entity children", moduleNode.getLabel())
                .isNotEmpty();

            for (TreeNode entityNode : moduleNode.getChildren()) {
                String label = entityNode.getLabel();
                log.info("  Entity: '" + label + "'");

                assertThat(label)
                    .as("Entity label should not be null")
                    .isNotNull();
                assertThat(label)
                    .as("Entity label should start with 'Entity '")
                    .startsWith("Entity ");
            }
        }
    }

    @Test
    void setSnapshotData_allLabelsAreNonNullAndNonEmpty() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshot();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        assertThat(tree.getRootNodes()).isNotEmpty();

        // Recursively verify all labels
        for (TreeNode rootNode : tree.getRootNodes()) {
            verifyLabelsRecursively(rootNode, 0);
        }
    }

    private void verifyLabelsRecursively(TreeNode node, int depth) {
        String label = node.getLabel();
        String indent = "  ".repeat(depth);

        log.info(indent + "'" + label + "' (expanded=" + node.isExpanded() + ", children=" + node.getChildren().size() + ")");

        assertThat(label)
            .as("Label at depth %d should not be null", depth)
            .isNotNull();
        assertThat(label)
            .as("Label at depth %d should not be empty", depth)
            .isNotEmpty();

        for (TreeNode child : node.getChildren()) {
            verifyLabelsRecursively(child, depth + 1);
        }
    }

    @Test
    void setSnapshotData_displaysEntityIdInTreeWhenAvailable() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshotWithEntityIds();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        TreeNode matchNode = rootNode.getChildren().get(0);
        TreeNode moduleNode = matchNode.getChildren().stream()
            .filter(n -> n.getLabel().equals("TestModule"))
            .findFirst()
            .orElseThrow();

        // Verify entity nodes show actual ENTITY_ID values
        List<String> entityLabels = moduleNode.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();
        assertThat(entityLabels).containsExactly("Entity 1001", "Entity 1002", "Entity 1003");
    }

    @Test
    void setSnapshotData_showsIndexFallbackWhenEntityIdMissing() {
        SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 800, 500, "http://localhost:8080", 1);
        SnapshotData snapshot = createTestSnapshotWithoutEntityIds();

        panel.setSnapshotData(snapshot);

        TreeView tree = panel.getEntityTree();
        TreeNode rootNode = tree.getRootNodes().get(0);
        TreeNode matchNode = rootNode.getChildren().get(0);
        TreeNode moduleNode = matchNode.getChildren().stream()
            .filter(n -> n.getLabel().equals("TestModule"))
            .findFirst()
            .orElseThrow();

        // Verify entity nodes show index fallback format
        List<String> entityLabels = moduleNode.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();
        assertThat(entityLabels).containsExactly("Entity (idx: 0)", "Entity (idx: 1)", "Entity (idx: 2)");
    }

    private SnapshotData createTestSnapshot() {
        Map<String, Map<String, List<Float>>> data = new HashMap<>();

        // SpawnModule with 3 entities
        Map<String, List<Float>> spawnData = new HashMap<>();
        spawnData.put("ENTITY_ID", List.of(101.0f, 102.0f, 103.0f));
        spawnData.put("ENTITY_TYPE", List.of(1.0f, 2.0f, 3.0f));
        spawnData.put("OWNER_ID", List.of(100.0f, 100.0f, 200.0f));
        spawnData.put("POSITION_X", List.of(50.0f, 100.0f, 150.0f));
        spawnData.put("POSITION_Y", List.of(60.0f, 110.0f, 160.0f));
        data.put("SpawnModule", spawnData);

        // MoveModule with 3 entities
        Map<String, List<Float>> moveData = new HashMap<>();
        moveData.put("ENTITY_ID", List.of(101.0f, 102.0f, 103.0f));
        moveData.put("VELOCITY_X", List.of(10.0f, 0.0f, -10.0f));
        moveData.put("VELOCITY_Y", List.of(0.0f, 10.0f, 0.0f));
        data.put("MoveModule", moveData);

        return new SnapshotData(1L, 42L, data);
    }

    private SnapshotData createTestSnapshotWithEntityIds() {
        Map<String, Map<String, List<Float>>> data = new HashMap<>();

        Map<String, List<Float>> moduleData = new HashMap<>();
        moduleData.put("ENTITY_ID", List.of(1001.0f, 1002.0f, 1003.0f));
        moduleData.put("VALUE", List.of(100.0f, 200.0f, 300.0f));
        data.put("TestModule", moduleData);

        return new SnapshotData(1L, 42L, data);
    }

    private SnapshotData createTestSnapshotWithoutEntityIds() {
        Map<String, Map<String, List<Float>>> data = new HashMap<>();

        Map<String, List<Float>> moduleData = new HashMap<>();
        // No ENTITY_ID component
        moduleData.put("VALUE", List.of(100.0f, 200.0f, 300.0f));
        data.put("TestModule", moduleData);

        return new SnapshotData(1L, 42L, data);
    }
}
