package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GameMasterService.
 */
@DisplayName("GameMasterService")
class GameMasterServiceTest {

    @Nested
    @DisplayName("parseGameMasterList")
    class ParseGameMasterList {

        @Test
        @DisplayName("parses single game master in standard order")
        void parsesSingleGameMaster_standardOrder() throws Exception {
            String json = "[{\"name\":\"TickCounter\",\"enabledMatches\":2}]";

            List<GameMasterInfo> gameMasters = invokeParseGameMasterList(json);

            assertThat(gameMasters).hasSize(1);
            assertThat(gameMasters.get(0).name()).isEqualTo("TickCounter");
            assertThat(gameMasters.get(0).enabledMatches()).isEqualTo(2);
        }

        @Test
        @DisplayName("parses game master with different field order")
        void parsesGameMaster_differentOrder() throws Exception {
            String json = "[{\"enabledMatches\":3,\"name\":\"AIController\"}]";

            List<GameMasterInfo> gameMasters = invokeParseGameMasterList(json);

            assertThat(gameMasters).hasSize(1);
            assertThat(gameMasters.get(0).name()).isEqualTo("AIController");
            assertThat(gameMasters.get(0).enabledMatches()).isEqualTo(3);
        }

        @Test
        @DisplayName("parses multiple game masters")
        void parsesMultipleGameMasters() throws Exception {
            String json = "[" +
                "{\"name\":\"TickCounter\",\"enabledMatches\":0}," +
                "{\"name\":\"AIController\",\"enabledMatches\":2}," +
                "{\"name\":\"EventLogger\",\"enabledMatches\":1}" +
                "]";

            List<GameMasterInfo> gameMasters = invokeParseGameMasterList(json);

            assertThat(gameMasters).hasSize(3);
            assertThat(gameMasters.get(0).name()).isEqualTo("TickCounter");
            assertThat(gameMasters.get(1).name()).isEqualTo("AIController");
            assertThat(gameMasters.get(2).name()).isEqualTo("EventLogger");
        }

        @Test
        @DisplayName("returns empty list for empty array")
        void returnsEmptyList_forEmptyArray() throws Exception {
            String json = "[]";

            List<GameMasterInfo> gameMasters = invokeParseGameMasterList(json);

            assertThat(gameMasters).isEmpty();
        }

        @Test
        @DisplayName("handles missing enabledMatches field")
        void handlesMissingEnabledMatchesField() throws Exception {
            String json = "[{\"name\":\"BasicGM\"}]";

            List<GameMasterInfo> gameMasters = invokeParseGameMasterList(json);

            assertThat(gameMasters).hasSize(1);
            assertThat(gameMasters.get(0).name()).isEqualTo("BasicGM");
            assertThat(gameMasters.get(0).enabledMatches()).isEqualTo(0);
        }

        @Test
        @DisplayName("handles game master with zero enabled matches")
        void handlesZeroEnabledMatches() throws Exception {
            String json = "[{\"name\":\"UnusedGM\",\"enabledMatches\":0}]";

            List<GameMasterInfo> gameMasters = invokeParseGameMasterList(json);

            assertThat(gameMasters).hasSize(1);
            assertThat(gameMasters.get(0).enabledMatches()).isEqualTo(0);
        }

        @Test
        @DisplayName("handles large enabledMatches values")
        void handlesLargeEnabledMatches() throws Exception {
            String json = "[{\"name\":\"PopularGM\",\"enabledMatches\":999}]";

            List<GameMasterInfo> gameMasters = invokeParseGameMasterList(json);

            assertThat(gameMasters).hasSize(1);
            assertThat(gameMasters.get(0).enabledMatches()).isEqualTo(999);
        }
    }

    @Nested
    @DisplayName("GameMasterInfo record")
    class GameMasterInfoRecord {

        @Test
        @DisplayName("equals works correctly")
        void equalsWorksCorrectly() {
            GameMasterInfo gm1 = new GameMasterInfo("Test", 1);
            GameMasterInfo gm2 = new GameMasterInfo("Test", 1);
            GameMasterInfo gm3 = new GameMasterInfo("Other", 1);

            assertThat(gm1).isEqualTo(gm2);
            assertThat(gm1).isNotEqualTo(gm3);
        }

        @Test
        @DisplayName("hashCode works correctly")
        void hashCodeWorksCorrectly() {
            GameMasterInfo gm1 = new GameMasterInfo("Test", 1);
            GameMasterInfo gm2 = new GameMasterInfo("Test", 1);

            assertThat(gm1.hashCode()).isEqualTo(gm2.hashCode());
        }

        @Test
        @DisplayName("toString includes field values")
        void toStringIncludesFields() {
            GameMasterInfo gm = new GameMasterInfo("TestGM", 5);

            String str = gm.toString();

            assertThat(str).contains("TestGM");
            assertThat(str).contains("5");
        }
    }

    @SuppressWarnings("unchecked")
    private List<GameMasterInfo> invokeParseGameMasterList(String json) throws Exception {
        GameMasterService service = new GameMasterService("http://localhost:8080");
        Method method = GameMasterService.class.getDeclaredMethod("parseGameMasterList", String.class);
        method.setAccessible(true);
        return (List<GameMasterInfo>) method.invoke(service, json);
    }
}
