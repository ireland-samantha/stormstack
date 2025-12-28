package com.lightningfirefly.engine.acceptance.test.domain;

import com.lightningfirefly.game.orchestrator.Snapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SnapshotParser")
class SnapshotParserTest {

    private static final String FULL_SNAPSHOT_JSON = """
            {"matchId":1,"tick":3,"data":{"SpawnModule":{"ENTITY_ID":[1.0],"ENTITY_TYPE":[100.0],"OWNER_ID":[1.0],"PLAYER_ID":[1.0]},"CheckersModule":{"ENTITY_ID":[1.0]},"RenderModule":{"ENTITY_ID":[1.0],"RESOURCE_ID":[1.0]},"MoveModule":{"ENTITY_ID":[1.0],"VELOCITY_X":[0.0],"VELOCITY_Y":[0.0],"VELOCITY_Z":[0.0],"POSITION_X":[200.0],"POSITION_Y":[150.0],"POSITION_Z":[0.0],"move":[1.0]}}}
            """;

    private static final String MULTI_ENTITY_JSON = """
            {"matchId":1,"tick":5,"data":{"SpawnModule":{"ENTITY_ID":[1.0,2.0,3.0],"ENTITY_TYPE":[100.0,101.0,102.0]},"MoveModule":{"ENTITY_ID":[1.0,2.0,3.0],"POSITION_X":[100.0,200.0,300.0],"POSITION_Y":[50.0,150.0,250.0]}}}
            """;

    @Nested
    @DisplayName("module parsing")
    class ModuleParsing {

        @Test
        @DisplayName("should parse all modules from snapshot")
        void parseAllModules() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            assertThat(parser.getModuleNames()).containsExactlyInAnyOrder(
                    "SpawnModule", "CheckersModule", "RenderModule", "MoveModule");
        }

        @Test
        @DisplayName("should detect module existence")
        void detectModuleExistence() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            assertThat(parser.hasModule("SpawnModule")).isTrue();
            assertThat(parser.hasModule("MoveModule")).isTrue();
            assertThat(parser.hasModule("RenderModule")).isTrue();
            assertThat(parser.hasModule("NonExistentModule")).isFalse();
        }
    }

    @Nested
    @DisplayName("component extraction")
    class ComponentExtraction {

        @Test
        @DisplayName("should get ENTITY_ID from SpawnModule")
        void getEntityIdFromSpawnModule() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            List<Float> entityIds = parser.getComponent("SpawnModule", "ENTITY_ID");

            assertThat(entityIds).containsExactly(1.0f);
        }

        @Test
        @DisplayName("should get position from MoveModule")
        void getPositionFromMoveModule() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            List<Float> posX = parser.getComponent("MoveModule", "POSITION_X");
            List<Float> posY = parser.getComponent("MoveModule", "POSITION_Y");

            assertThat(posX).containsExactly(200.0f);
            assertThat(posY).containsExactly(150.0f);
        }

        @Test
        @DisplayName("should get RESOURCE_ID from RenderModule")
        void getResourceIdFromRenderModule() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            List<Float> resourceIds = parser.getComponent("RenderModule", "RESOURCE_ID");

            assertThat(resourceIds).containsExactly(1.0f);
        }

        @Test
        @DisplayName("should get velocity from MoveModule")
        void getVelocityFromMoveModule() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            List<Float> velX = parser.getComponent("MoveModule", "VELOCITY_X");
            List<Float> velY = parser.getComponent("MoveModule", "VELOCITY_Y");

            assertThat(velX).containsExactly(0.0f);
            assertThat(velY).containsExactly(0.0f);
        }

        @Test
        @DisplayName("should return empty list for non-existent component")
        void returnEmptyForNonExistentComponent() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            List<Float> missing = parser.getComponent("SpawnModule", "NON_EXISTENT");

            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for non-existent module")
        void returnEmptyForNonExistentModule() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            List<Float> missing = parser.getComponent("NonExistentModule", "ENTITY_ID");

            assertThat(missing).isEmpty();
        }
    }

    @Nested
    @DisplayName("single value extraction")
    class SingleValueExtraction {

        @Test
        @DisplayName("should get single component value")
        void getSingleValue() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            assertThat(parser.getComponentValue("MoveModule", "POSITION_X"))
                    .hasValue(200.0f);
            assertThat(parser.getComponentValue("MoveModule", "POSITION_Y"))
                    .hasValue(150.0f);
        }

        @Test
        @DisplayName("should return empty for missing value")
        void returnEmptyForMissingValue() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            assertThat(parser.getComponentValue("SpawnModule", "MISSING"))
                    .isEmpty();
        }

        @Test
        @DisplayName("should get value at specific index")
        void getValueAtIndex() {
            SnapshotParser parser = SnapshotParser.parse(MULTI_ENTITY_JSON);

            assertThat(parser.getComponentValue("MoveModule", "POSITION_X", 0))
                    .hasValue(100.0f);
            assertThat(parser.getComponentValue("MoveModule", "POSITION_X", 1))
                    .hasValue(200.0f);
            assertThat(parser.getComponentValue("MoveModule", "POSITION_X", 2))
                    .hasValue(300.0f);
        }

        @Test
        @DisplayName("should return empty for out of bounds index")
        void returnEmptyForOutOfBoundsIndex() {
            SnapshotParser parser = SnapshotParser.parse(MULTI_ENTITY_JSON);

            assertThat(parser.getComponentValue("MoveModule", "POSITION_X", 999))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("multi-entity parsing")
    class MultiEntityParsing {

        @Test
        @DisplayName("should parse multiple entities")
        void parseMultipleEntities() {
            SnapshotParser parser = SnapshotParser.parse(MULTI_ENTITY_JSON);

            List<Float> entityIds = parser.getComponent("SpawnModule", "ENTITY_ID");
            List<Float> posX = parser.getComponent("MoveModule", "POSITION_X");
            List<Float> posY = parser.getComponent("MoveModule", "POSITION_Y");

            assertThat(entityIds).containsExactly(1.0f, 2.0f, 3.0f);
            assertThat(posX).containsExactly(100.0f, 200.0f, 300.0f);
            assertThat(posY).containsExactly(50.0f, 150.0f, 250.0f);
        }
    }

    @Nested
    @DisplayName("toSnapshot conversion")
    class ToSnapshotConversion {

        @Test
        @DisplayName("should convert to Snapshot with merged data")
        void convertToSnapshot() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            Snapshot snapshot = parser.toSnapshot();

            assertThat(snapshot.components()).containsKey("MergedData");
            var mergedData = snapshot.components().get("MergedData");

            assertThat(mergedData.get("ENTITY_ID")).containsExactly(1.0f);
            assertThat(mergedData.get("POSITION_X")).containsExactly(200.0f);
            assertThat(mergedData.get("POSITION_Y")).containsExactly(150.0f);
            assertThat(mergedData.get("RESOURCE_ID")).containsExactly(1.0f);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("should produce readable output")
        void produceReadableOutput() {
            SnapshotParser parser = SnapshotParser.parse(FULL_SNAPSHOT_JSON);

            String output = parser.toString();

            assertThat(output).contains("SnapshotParser");
            assertThat(output).contains("MoveModule");
            assertThat(output).contains("POSITION_X");
        }
    }
}
