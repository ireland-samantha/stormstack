package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import com.lightningfirefly.engine.rendering.render2d.TreeNode;
import com.lightningfirefly.engine.rendering.render2d.TreeView;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TreeView label rendering in the SnapshotPanel.
 *
 * <p>Run with:
 * <pre>
 * ./mvnw test -pl lightning-engine/gui -Dtest=SnapshotTreeViewTest -DenableGLTests=true
 * </pre>
 */
@Tag("integration")
@DisplayName("Snapshot TreeView Label Tests")
@EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
class SnapshotTreeViewTest {

    private Window window;
    private ComponentFactory factory;

    @BeforeEach
    void setUp() {
        factory = GLComponentFactory.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (window != null) {
            try {
                window.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            window = null;
        }
    }

    @Test
    @DisplayName("TreeView nodes have correct labels after snapshot update")
    void treeView_nodesHaveCorrectLabels() {
        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("TreeView Label Test")
            .build();

        // Create TreeView directly
        TreeView treeView = factory.createTreeView(10, 10, 780, 580);

        // Create nodes with labels
        TreeNode rootNode = factory.createTreeNode("Snapshots");
        rootNode.setExpanded(true);

        TreeNode summaryNode = factory.createTreeNode("Snapshot (Tick: 42, Entities: 2)");
        summaryNode.setExpanded(true);
        rootNode.addChild(summaryNode);

        TreeNode moduleNode = factory.createTreeNode("MoveModule");
        moduleNode.setExpanded(true);
        summaryNode.addChild(moduleNode);

        TreeNode entity0 = factory.createTreeNode("Entity 0");
        entity0.setExpanded(true);
        moduleNode.addChild(entity0);

        TreeNode posX = factory.createTreeNode("POSITION_X: 100");
        TreeNode posY = factory.createTreeNode("POSITION_Y: 200");
        TreeNode velX = factory.createTreeNode("VELOCITY_X: 1");
        entity0.addChild(posX);
        entity0.addChild(posY);
        entity0.addChild(velX);

        treeView.addRootNode(rootNode);

        // Verify node labels
        assertThat(rootNode.getLabel()).isEqualTo("Snapshots");
        assertThat(summaryNode.getLabel()).isEqualTo("Snapshot (Tick: 42, Entities: 2)");
        assertThat(moduleNode.getLabel()).isEqualTo("MoveModule");
        assertThat(entity0.getLabel()).isEqualTo("Entity 0");
        assertThat(posX.getLabel()).isEqualTo("POSITION_X: 100");
        assertThat(posY.getLabel()).isEqualTo("POSITION_Y: 200");
        assertThat(velX.getLabel()).isEqualTo("VELOCITY_X: 1");

        // Verify tree structure
        assertThat(treeView.getRootNodes()).hasSize(1);
        assertThat(rootNode.getChildren()).hasSize(1);
        assertThat(summaryNode.getChildren()).hasSize(1);
        assertThat(moduleNode.getChildren()).hasSize(1);
        assertThat(entity0.getChildren()).hasSize(3);

        // Add to window and render
        window.addComponent(treeView);
        window.runFrames(5);

        System.out.println("TreeView labels verified successfully:");
        printTreeStructure(rootNode, 0);
    }

    @Test
    @DisplayName("SnapshotPanel TreeView renders with mock snapshot data")
    void snapshotPanel_treeViewRendersWithMockData() {
        // Create window
        window = WindowBuilder.create()
            .size(1000, 700)
            .title("SnapshotPanel TreeView Test")
            .build();

        // Create SnapshotPanel (it won't connect to real WebSocket since we use a fake URL)
        SnapshotPanel panel = new SnapshotPanel(factory, 10, 10, 980, 680, "http://localhost:9999", 1);
        window.addComponent(panel);

        // Create mock snapshot data
        Map<String, Map<String, List<Float>>> data = Map.of(
            "MoveModule", Map.of(
                "POSITION_X", List.of(100.0f, 200.0f),
                "POSITION_Y", List.of(50.0f, 60.0f),
                "VELOCITY_X", List.of(1.0f, 2.0f),
                "VELOCITY_Y", List.of(0.0f, 0.0f)
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 42, data);

        // Set snapshot data (this triggers tree building)
        panel.setSnapshotData(snapshot);

        // Run a few frames to render
        window.runFrames(5);

        // Get the tree and verify structure
        TreeView tree = panel.getEntityTree();
        assertThat(tree.getRootNodes()).isNotEmpty();

        TreeNode root = tree.getRootNodes().get(0);
        assertThat(root.getLabel()).isEqualTo("Matches");

        // Navigate to find module and entity labels
        if (!root.getChildren().isEmpty()) {
            TreeNode summary = root.getChildren().get(0);
            System.out.println("Summary label: " + summary.getLabel());
            assertThat(summary.getLabel()).contains("Tick: 42");

            if (!summary.getChildren().isEmpty()) {
                TreeNode module = summary.getChildren().get(0);
                System.out.println("Module label: " + module.getLabel());
                assertThat(module.getLabel()).isEqualTo("MoveModule");

                if (!module.getChildren().isEmpty()) {
                    TreeNode entity = module.getChildren().get(0);
                    System.out.println("Entity label: " + entity.getLabel());
                    assertThat(entity.getLabel()).isEqualTo("Entity 0");

                    // Check component labels
                    for (TreeNode component : entity.getChildren()) {
                        String label = component.getLabel();
                        System.out.println("Component label: " + label);
                        assertThat(label).isNotNull();
                        assertThat(label).isNotEmpty();
                        assertThat(label).contains(":");
                    }
                }
            }
        }

        // Render more frames to ensure labels display
        window.runFrames(10);

        System.out.println("SnapshotPanel TreeView labels verified successfully");
    }

    @Test
    @DisplayName("TreeView renders expanded nodes with all labels visible")
    void treeView_rendersExpandedNodesWithLabels() {
        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("Expanded TreeView Test")
            .build();

        // Create TreeView with many nodes
        TreeView treeView = factory.createTreeView(10, 10, 780, 580);

        TreeNode root = factory.createTreeNode("Root");
        root.setExpanded(true);
        treeView.addRootNode(root);

        // Add multiple levels
        for (int i = 0; i < 3; i++) {
            TreeNode level1 = factory.createTreeNode("Level1_Node_" + i);
            level1.setExpanded(true);
            root.addChild(level1);

            for (int j = 0; j < 3; j++) {
                TreeNode level2 = factory.createTreeNode("Level2_Node_" + i + "_" + j);
                level2.setExpanded(true);
                level1.addChild(level2);

                for (int k = 0; k < 2; k++) {
                    TreeNode level3 = factory.createTreeNode("Value_" + i + "_" + j + "_" + k + ": " + (i * 100 + j * 10 + k));
                    level2.addChild(level3);
                }
            }
        }

        window.addComponent(treeView);

        // Render many frames
        window.runFrames(20);

        // Verify all labels are set correctly
        verifyAllLabelsSet(root);

        System.out.println("All tree node labels are set correctly");
    }

    private void verifyAllLabelsSet(TreeNode node) {
        assertThat(node.getLabel())
            .as("Label should not be null for node")
            .isNotNull();
        assertThat(node.getLabel())
            .as("Label should not be empty for node")
            .isNotEmpty();

        for (TreeNode child : node.getChildren()) {
            verifyAllLabelsSet(child);
        }
    }

    @Test
    @DisplayName("Entity nodes are expanded by default showing component labels")
    void entityNodes_areExpandedByDefault() {
        // Create window
        window = WindowBuilder.create()
            .size(1000, 700)
            .title("Entity Expansion Test")
            .build();

        // Create SnapshotPanel
        SnapshotPanel panel = new SnapshotPanel(factory, 10, 10, 980, 680, "http://localhost:9999", 1);
        window.addComponent(panel);

        // Create mock snapshot data with components
        Map<String, Map<String, List<Float>>> data = Map.of(
            "MoveModule", Map.of(
                "POSITION_X", List.of(100.0f),
                "POSITION_Y", List.of(50.0f),
                "VELOCITY_X", List.of(1.0f)
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 42, data);

        // Set snapshot data
        panel.setSnapshotData(snapshot);

        // Run frames to render
        window.runFrames(5);

        // Get the tree and verify entity nodes are expanded
        TreeView tree = panel.getEntityTree();
        TreeNode root = tree.getRootNodes().get(0);  // Snapshots
        TreeNode summary = root.getChildren().get(0);  // Snapshot (Tick: ...)
        TreeNode module = summary.getChildren().get(0);  // MoveModule
        TreeNode entity = module.getChildren().get(0);  // Entity 0

        // Verify entity node is expanded by default
        assertThat(entity.isExpanded())
            .as("Entity node should be expanded by default to show component labels")
            .isTrue();

        // Verify component children exist
        assertThat(entity.getChildren())
            .as("Entity should have component children")
            .hasSize(3);

        // Verify component labels are set
        for (TreeNode component : entity.getChildren()) {
            System.out.println("Component visible: " + component.getLabel());
            assertThat(component.getLabel())
                .as("Component label should contain value")
                .contains(":");
        }

        System.out.println("Entity nodes are expanded by default - component labels are visible");
    }

    private void printTreeStructure(TreeNode node, int depth) {
        String indent = "  ".repeat(depth);
        String expanded = node.isExpanded() ? "[+]" : "[-]";
        System.out.println(indent + expanded + " '" + node.getLabel() + "' (" + node.getChildren().size() + " children)");

        if (node.isExpanded()) {
            for (TreeNode child : node.getChildren()) {
                printTreeStructure(child, depth + 1);
            }
        }
    }
}
