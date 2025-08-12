package com.lightningfirefly.engine.gui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.TreeNode;
import com.lightningfirefly.engine.rendering.render2d.TreeView;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLTreeNode;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLTreeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the data flow from API responses
 * to GUI components.
 */
class ApiIntegrationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void parseSnapshotJson_withRealApiFormat() throws Exception {
        // Real API response format from SnapshotWebSocket
        String json = """
            {
                "matchId": 1,
                "tick": 42,
                "data": {
                    "SpawnModule": {
                        "ENTITY_TYPE": [1, 2, 3, 1, 2],
                        "OWNER_ID": [100, 100, 200, 300, 300],
                        "POSITION_X": [50, 100, 150, 200, 250],
                        "POSITION_Y": [60, 110, 160, 210, 260]
                    },
                    "MoveModule": {
                        "VELOCITY_X": [10, 0, -10, 5, -5],
                        "VELOCITY_Y": [0, 10, 0, -5, 5]
                    }
                }
            }
            """;

        SnapshotData snapshot = parseSnapshotResponse(json);

        assertThat(snapshot.matchId()).isEqualTo(1L);
        assertThat(snapshot.tick()).isEqualTo(42L);
        assertThat(snapshot.getModuleNames()).containsExactlyInAnyOrder("SpawnModule", "MoveModule");
        assertThat(snapshot.getEntityCount()).isEqualTo(5);

        // Verify module data
        Map<String, List<Float>> spawnData = snapshot.getModuleData("SpawnModule");
        assertThat(spawnData).isNotNull();
        assertThat(spawnData.get("ENTITY_TYPE")).containsExactly(1.0f, 2.0f, 3.0f, 1.0f, 2.0f);
        assertThat(spawnData.get("OWNER_ID")).containsExactly(100.0f, 100.0f, 200.0f, 300.0f, 300.0f);

        Map<String, List<Float>> moveData = snapshot.getModuleData("MoveModule");
        assertThat(moveData).isNotNull();
        assertThat(moveData.get("VELOCITY_X")).containsExactly(10.0f, 0.0f, -10.0f, 5.0f, -5.0f);
    }

    @Test
    void parseSnapshotJson_withEmptyData() throws Exception {
        String json = """
            {
                "matchId": 1,
                "tick": 0,
                "data": {}
            }
            """;

        SnapshotData snapshot = parseSnapshotResponse(json);

        assertThat(snapshot.matchId()).isEqualTo(1L);
        assertThat(snapshot.tick()).isZero();
        assertThat(snapshot.getModuleNames()).isEmpty();
        assertThat(snapshot.getEntityCount()).isZero();
    }

    @Test
    void parseSnapshotJson_withNullValues() throws Exception {
        // API may return NaN for NULL sentinel values
        String json = """
            {
                "matchId": 1,
                "tick": 10,
                "data": {
                    "SpawnModule": {
                        "ENTITY_TYPE": [1, "NaN", 3],
                        "OWNER_ID": [100, "NaN", 300]
                    }
                }
            }
            """;

        SnapshotData snapshot = parseSnapshotResponse(json);

        Map<String, List<Float>> spawnData = snapshot.getModuleData("SpawnModule");
        assertThat(spawnData).isNotNull();
        // NaN is the NULL sentinel
        assertThat(spawnData.get("ENTITY_TYPE").get(0)).isEqualTo(1.0f);
        assertThat(spawnData.get("ENTITY_TYPE").get(1)).isNaN();
        assertThat(spawnData.get("ENTITY_TYPE").get(2)).isEqualTo(3.0f);
    }

    @Test
    void buildTreeFromSnapshot_createsCorrectHierarchy() {
        // Create snapshot data
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of(
                "ENTITY_TYPE", List.of(1.0f, 2.0f),
                "POSITION_X", List.of(100.0f, 200.0f),
                "POSITION_Y", List.of(50.0f, 75.0f)
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 42L, data);

        // Build tree view from snapshot
        TreeView treeView = new GLTreeView(0, 0, 300, 400);
        buildEntityTree(treeView, snapshot);

        // Verify tree structure
        assertThat(treeView.getRootNodes()).hasSize(1);
        TreeNode summaryNode = treeView.getRootNodes().get(0);
        assertThat(summaryNode.getLabel()).contains("Tick: 42");
        assertThat(summaryNode.getLabel()).contains("Entities: 2");

        // Module node
        assertThat(summaryNode.getChildren()).hasSize(1);
        TreeNode moduleNode = summaryNode.getChildren().get(0);
        assertThat(moduleNode.getLabel()).isEqualTo("SpawnModule");

        // Entity nodes
        assertThat(moduleNode.getChildren()).hasSize(2);
        TreeNode entity0 = moduleNode.getChildren().get(0);
        assertThat(entity0.getLabel()).isEqualTo("Entity 0");

        // Component values for entity 0
        assertThat(entity0.getChildren()).hasSize(3);
        List<String> componentTexts = entity0.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();
        assertThat(componentTexts).containsAnyOf(
            "ENTITY_TYPE: 1",
            "POSITION_X: 100",
            "POSITION_Y: 50"
        );
    }

    @Test
    void buildTreeFromSnapshot_handlesLargeDataset() {
        // Create large dataset with 1000 entities
        int entityCount = 1000;
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of(
                "ENTITY_TYPE", generateFloatList(entityCount, i -> (float) (i % 5)),
                "OWNER_ID", generateFloatList(entityCount, i -> (float) (i * 10))
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 100L, data);

        TreeView treeView = new GLTreeView(0, 0, 300, 400);
        buildEntityTree(treeView, snapshot);

        // Should handle large dataset without issues
        assertThat(treeView.getRootNodes()).hasSize(1);
        TreeNode summaryNode = treeView.getRootNodes().get(0);
        assertThat(summaryNode.getLabel()).contains("Entities: 1000");
    }

    @Test
    void buildTreeFromSnapshot_handlesMultipleModules() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of(
                "ENTITY_TYPE", List.of(1.0f, 2.0f, 3.0f)
            ),
            "MoveModule", Map.of(
                "VELOCITY_X", List.of(10.0f, 20.0f, 30.0f)
            ),
            "HealthModule", Map.of(
                "HP", List.of(100.0f, 80.0f, 60.0f)
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 50L, data);

        TreeView treeView = new GLTreeView(0, 0, 300, 400);
        buildEntityTree(treeView, snapshot);

        assertThat(treeView.getRootNodes()).hasSize(1);
        TreeNode summaryNode = treeView.getRootNodes().get(0);
        // 3 modules
        assertThat(summaryNode.getChildren()).hasSize(3);

        List<String> moduleNames = summaryNode.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();
        assertThat(moduleNames).containsExactlyInAnyOrder("SpawnModule", "MoveModule", "HealthModule");
    }

    @Test
    void snapshotData_formatsNullValues() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "TestModule", Map.of(
                "VALUE", List.of(1.0f, Float.NaN, 3.0f)
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 1L, data);

        // Value at index 1 is the NULL sentinel (NaN)
        List<Float> values = snapshot.getModuleData("TestModule").get("VALUE");
        assertThat(formatComponentValue("VALUE", values.get(1))).isEqualTo("null");
        assertThat(formatComponentValue("VALUE", values.get(0))).isEqualTo("1");
    }

    @Test
    void resourceListJson_parsesCorrectly() throws Exception {
        String json = """
            [
                {"id": 1, "name": "texture1.png", "type": "TEXTURE", "size": 1024},
                {"id": 2, "name": "texture2.png", "type": "TEXTURE", "size": 2048},
                {"id": 3, "name": "sound.wav", "type": "AUDIO", "size": 8192}
            ]
            """;

        List<Map<String, Object>> resources = objectMapper.readValue(
            json, new TypeReference<>() {});

        assertThat(resources).hasSize(3);
        assertThat(resources.get(0).get("id")).isEqualTo(1);
        assertThat(resources.get(0).get("name")).isEqualTo("texture1.png");
        assertThat(resources.get(0).get("type")).isEqualTo("TEXTURE");
    }

    @Test
    void resourceListJson_parsesRealApiFormat() throws Exception {
        // This is the actual format returned by the ResourceResource REST API
        String json = """
            [
                {"resourceId": 1, "resourceName": "texture1.png", "resourceType": "TEXTURE", "size": 1024},
                {"resourceId": 2, "resourceName": "texture2.png", "resourceType": "TEXTURE", "size": 2048},
                {"resourceId": 3, "resourceName": "sound.wav", "resourceType": "AUDIO", "size": 8192}
            ]
            """;

        List<Map<String, Object>> resources = objectMapper.readValue(
            json, new TypeReference<>() {});

        assertThat(resources).hasSize(3);

        // Verify first resource
        assertThat(resources.get(0).get("resourceId")).isEqualTo(1);
        assertThat(resources.get(0).get("resourceName")).isEqualTo("texture1.png");
        assertThat(resources.get(0).get("resourceType")).isEqualTo("TEXTURE");

        // Verify we can extract values like HttpResourceAdapter does
        for (Map<String, Object> resource : resources) {
            long resourceId = ((Number) resource.get("resourceId")).longValue();
            String resourceName = (String) resource.get("resourceName");
            String resourceType = (String) resource.get("resourceType");

            assertThat(resourceId).isPositive();
            assertThat(resourceName).isNotBlank();
            assertThat(resourceType).isNotBlank();
        }
    }

    @Test
    void treeView_preservesExpansionState() {
        // Create initial tree structure
        TreeView treeView = new GLTreeView(0, 0, 300, 400);

        TreeNode root = new GLTreeNode("Root");
        root.setExpanded(true);

        TreeNode child1 = new GLTreeNode("Child 1");
        child1.setExpanded(true);
        root.addChild(child1);

        TreeNode child2 = new GLTreeNode("Child 2");
        child2.setExpanded(false);
        root.addChild(child2);

        TreeNode grandChild = new GLTreeNode("Grandchild");
        grandChild.setExpanded(true);
        child1.addChild(grandChild);

        treeView.addRootNode(root);

        // Verify initial expansion state
        assertThat(root.isExpanded()).isTrue();
        assertThat(child1.isExpanded()).isTrue();
        assertThat(child2.isExpanded()).isFalse();
        assertThat(grandChild.isExpanded()).isTrue();

        // Simulate what SnapshotPanel now does - save state before clear
        java.util.Map<String, Boolean> savedState = new java.util.HashMap<>();
        saveTreeExpansionState(treeView.getRootNodes().get(0), "", savedState);

        // Clear and rebuild
        treeView.clearNodes();
        assertThat(treeView.getRootNodes()).isEmpty();

        // Rebuild tree
        TreeNode newRoot = new GLTreeNode("Root");
        TreeNode newChild1 = new GLTreeNode("Child 1");
        TreeNode newChild2 = new GLTreeNode("Child 2");
        TreeNode newGrandChild = new GLTreeNode("Grandchild");

        newChild1.addChild(newGrandChild);
        newRoot.addChild(newChild1);
        newRoot.addChild(newChild2);
        treeView.addRootNode(newRoot);

        // Restore state
        restoreTreeExpansionState(newRoot, "", savedState);

        // Verify restored expansion state
        assertThat(newRoot.isExpanded()).isTrue();
        assertThat(newChild1.isExpanded()).isTrue();
        assertThat(newChild2.isExpanded()).isFalse();
        assertThat(newGrandChild.isExpanded()).isTrue();
    }

    @Test
    void componentNames_displayCorrectly() {
        // Test that component names from API are displayed correctly in tree
        Map<String, Map<String, List<Float>>> data = new java.util.HashMap<>();
        Map<String, List<Float>> moduleData = new java.util.HashMap<>();
        moduleData.put("POSITION_X", List.of(100.0f));
        moduleData.put("POSITION_Y", List.of(200.0f));
        moduleData.put("ENTITY_TYPE", List.of(1.0f));
        moduleData.put("OWNER_ID", List.of(42.0f));
        data.put("SpawnModule", moduleData);

        SnapshotData snapshot = new SnapshotData(1L, 1L, data);

        TreeView treeView = new GLTreeView(0, 0, 300, 400);
        buildEntityTree(treeView, snapshot);

        // Navigate to entity node
        TreeNode summaryNode = treeView.getRootNodes().get(0);
        TreeNode moduleNode = summaryNode.getChildren().get(0);
        TreeNode entityNode = moduleNode.getChildren().get(0);

        // Get all component labels
        List<String> componentLabels = entityNode.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();

        // Verify component names are displayed with their values
        assertThat(componentLabels).containsExactlyInAnyOrder(
            "ENTITY_TYPE: 1",
            "OWNER_ID: 42",
            "POSITION_X: 100",
            "POSITION_Y: 200"
        );
    }

    @Test
    void componentNames_sortedConsistently() {
        // Test that component names are sorted for consistent display order
        Map<String, Map<String, List<Float>>> data = new java.util.LinkedHashMap<>();
        Map<String, List<Float>> moduleData = new java.util.LinkedHashMap<>();
        // Add in non-alphabetical order
        moduleData.put("Z_COMPONENT", List.of(1.0f));
        moduleData.put("A_COMPONENT", List.of(2.0f));
        moduleData.put("M_COMPONENT", List.of(3.0f));
        data.put("TestModule", moduleData);

        SnapshotData snapshot = new SnapshotData(1L, 1L, data);

        // Build tree using updated logic that sorts component names
        TreeView treeView = new GLTreeView(0, 0, 300, 400);
        buildEntityTreeWithSorting(treeView, snapshot);

        // Navigate to entity node
        TreeNode summaryNode = treeView.getRootNodes().get(0);
        TreeNode moduleNode = summaryNode.getChildren().get(0);
        TreeNode entityNode = moduleNode.getChildren().get(0);

        // Verify component names are sorted alphabetically
        List<String> componentLabels = entityNode.getChildren().stream()
            .map(TreeNode::getLabel)
            .toList();

        assertThat(componentLabels).containsExactly(
            "A_COMPONENT: 2",
            "M_COMPONENT: 3",
            "Z_COMPONENT: 1"
        );
    }

    // Helper to save expansion state recursively
    private void saveTreeExpansionState(TreeNode node, String path, java.util.Map<String, Boolean> state) {
        String key = path.isEmpty() ? node.getLabel() : path + "/" + node.getLabel();
        state.put(key, node.isExpanded());
        for (TreeNode child : node.getChildren()) {
            saveTreeExpansionState(child, key, state);
        }
    }

    // Helper to restore expansion state recursively
    private void restoreTreeExpansionState(TreeNode node, String path, java.util.Map<String, Boolean> state) {
        String key = path.isEmpty() ? node.getLabel() : path + "/" + node.getLabel();
        Boolean expanded = state.get(key);
        if (expanded != null) {
            node.setExpanded(expanded);
        }
        for (TreeNode child : node.getChildren()) {
            restoreTreeExpansionState(child, key, state);
        }
    }

    // Helper that mirrors updated SnapshotPanel logic with component sorting
    private void buildEntityTreeWithSorting(TreeView treeView, SnapshotData snapshot) {
        treeView.clearNodes();

        if (snapshot.data() == null || snapshot.data().isEmpty()) {
            TreeNode emptyNode = new GLTreeNode("No entities in snapshot");
            treeView.addRootNode(emptyNode);
            return;
        }

        int entityCount = snapshot.getEntityCount();

        TreeNode summaryNode = new GLTreeNode(
            String.format("Snapshot (Tick: %d, Entities: %d)", snapshot.tick(), entityCount)
        );
        summaryNode.setExpanded(true);
        treeView.addRootNode(summaryNode);

        for (String moduleName : snapshot.getModuleNames()) {
            Map<String, List<Float>> moduleData = snapshot.getModuleData(moduleName);
            if (moduleData == null || moduleData.isEmpty()) {
                continue;
            }

            TreeNode moduleNode = new GLTreeNode(moduleName);
            moduleNode.setExpanded(true);
            summaryNode.addChild(moduleNode);

            // Sort component names for consistent ordering
            List<String> componentNames = new java.util.ArrayList<>(moduleData.keySet());
            componentNames.sort(String::compareTo);
            if (componentNames.isEmpty()) {
                continue;
            }

            int moduleEntityCount = moduleData.get(componentNames.get(0)).size();

            for (int i = 0; i < moduleEntityCount; i++) {
                TreeNode entityNode = new GLTreeNode("Entity " + i);

                for (String componentName : componentNames) {
                    List<Float> values = moduleData.get(componentName);
                    if (i < values.size()) {
                        float value = values.get(i);
                        String displayValue = formatComponentValue(componentName, value);
                        TreeNode componentNode = new GLTreeNode(
                            componentName + ": " + displayValue
                        );
                        entityNode.addChild(componentNode);
                    }
                }

                moduleNode.addChild(entityNode);
            }
        }
    }

    // Helper method to simulate parseSnapshotResponse from SnapshotWebSocketClient
    private SnapshotData parseSnapshotResponse(String json) throws Exception {
        Map<String, Object> response = objectMapper.readValue(json, new TypeReference<>() {});

        long matchId = ((Number) response.getOrDefault("matchId", 0)).longValue();
        long tick = ((Number) response.getOrDefault("tick", 0)).longValue();

        @SuppressWarnings("unchecked")
        Map<String, Map<String, List<Object>>> data =
            (Map<String, Map<String, List<Object>>>) response.get("data");

        // Convert Object lists to Float lists
        Map<String, Map<String, List<Float>>> snapshotData = new java.util.HashMap<>();
        if (data != null) {
            for (var moduleEntry : data.entrySet()) {
                Map<String, List<Float>> componentMap = new java.util.HashMap<>();
                for (var componentEntry : moduleEntry.getValue().entrySet()) {
                    List<Float> values = componentEntry.getValue().stream()
                        .map(obj -> {
                            if (obj instanceof Number n) {
                                return n.floatValue();
                            } else if (obj instanceof String s && "NaN".equals(s)) {
                                return Float.NaN;
                            }
                            return Float.NaN;
                        })
                        .toList();
                    componentMap.put(componentEntry.getKey(), values);
                }
                snapshotData.put(moduleEntry.getKey(), componentMap);
            }
        }

        return new SnapshotData(matchId, tick, snapshotData);
    }

    // Helper to build entity tree (mirrors SnapshotPanel logic)
    private void buildEntityTree(TreeView treeView, SnapshotData snapshot) {
        treeView.clearNodes();

        if (snapshot.data() == null || snapshot.data().isEmpty()) {
            TreeNode emptyNode = new GLTreeNode("No entities in snapshot");
            treeView.addRootNode(emptyNode);
            return;
        }

        int entityCount = snapshot.getEntityCount();

        TreeNode summaryNode = new GLTreeNode(
            String.format("Snapshot (Tick: %d, Entities: %d)", snapshot.tick(), entityCount)
        );
        summaryNode.setExpanded(true);
        treeView.addRootNode(summaryNode);

        for (String moduleName : snapshot.getModuleNames()) {
            Map<String, List<Float>> moduleData = snapshot.getModuleData(moduleName);
            if (moduleData == null || moduleData.isEmpty()) {
                continue;
            }

            TreeNode moduleNode = new GLTreeNode(moduleName);
            moduleNode.setExpanded(true);
            summaryNode.addChild(moduleNode);

            List<String> componentNames = new java.util.ArrayList<>(moduleData.keySet());
            if (componentNames.isEmpty()) {
                continue;
            }

            int moduleEntityCount = moduleData.get(componentNames.get(0)).size();

            for (int i = 0; i < moduleEntityCount; i++) {
                TreeNode entityNode = new GLTreeNode("Entity " + i);

                for (String componentName : componentNames) {
                    List<Float> values = moduleData.get(componentName);
                    if (i < values.size()) {
                        float value = values.get(i);
                        String displayValue = formatComponentValue(componentName, value);
                        TreeNode componentNode = new GLTreeNode(
                            componentName + ": " + displayValue
                        );
                        entityNode.addChild(componentNode);
                    }
                }

                moduleNode.addChild(entityNode);
            }
        }
    }

    private String formatComponentValue(String componentName, float value) {
        if (Float.isNaN(value)) {
            return "null";
        }
        // Display integer values without decimal point if they're whole numbers
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private List<Float> generateFloatList(int count, java.util.function.IntFunction<Float> generator) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(generator::apply)
            .toList();
    }
}
