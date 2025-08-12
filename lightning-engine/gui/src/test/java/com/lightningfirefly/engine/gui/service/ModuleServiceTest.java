package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ModuleService.
 */
class ModuleServiceTest {

    @Test
    void parseModuleList_standardOrder() throws Exception {
        String json = "[{\"name\":\"MoveModule\",\"flagComponentName\":\"MoveFlag\",\"enabledMatches\":2}]";

        List<ModuleInfo> modules = invokeParseModuleList(json);

        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).name()).isEqualTo("MoveModule");
        assertThat(modules.get(0).flagComponent()).isEqualTo("MoveFlag");
        assertThat(modules.get(0).enabledMatches()).isEqualTo(2);
    }

    @Test
    void parseModuleList_differentOrder() throws Exception {
        // Fields in different order than expected
        String json = "[{\"flagComponentName\":\"SpawnFlag\",\"enabledMatches\":0,\"name\":\"SpawnModule\"}]";

        List<ModuleInfo> modules = invokeParseModuleList(json);

        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).name()).isEqualTo("SpawnModule");
        assertThat(modules.get(0).flagComponent()).isEqualTo("SpawnFlag");
        assertThat(modules.get(0).enabledMatches()).isEqualTo(0);
    }

    @Test
    void parseModuleList_nullFlagComponent() throws Exception {
        String json = "[{\"name\":\"RenderModule\",\"flagComponentName\":null,\"enabledMatches\":1}]";

        List<ModuleInfo> modules = invokeParseModuleList(json);

        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).name()).isEqualTo("RenderModule");
        assertThat(modules.get(0).flagComponent()).isNull();
        assertThat(modules.get(0).enabledMatches()).isEqualTo(1);
    }

    @Test
    void parseModuleList_multipleModules() throws Exception {
        String json = "[" +
            "{\"name\":\"SpawnModule\",\"flagComponentName\":null,\"enabledMatches\":0}," +
            "{\"name\":\"RenderModule\",\"flagComponentName\":\"RenderFlag\",\"enabledMatches\":3}," +
            "{\"name\":\"MoveModule\",\"flagComponentName\":\"MoveFlag\",\"enabledMatches\":1}" +
            "]";

        List<ModuleInfo> modules = invokeParseModuleList(json);

        assertThat(modules).hasSize(3);
        assertThat(modules.get(0).name()).isEqualTo("SpawnModule");
        assertThat(modules.get(1).name()).isEqualTo("RenderModule");
        assertThat(modules.get(2).name()).isEqualTo("MoveModule");
    }

    @Test
    void parseModuleList_emptyArray() throws Exception {
        String json = "[]";

        List<ModuleInfo> modules = invokeParseModuleList(json);

        assertThat(modules).isEmpty();
    }

    @Test
    void parseModuleList_missingOptionalFields() throws Exception {
        // Only name field present
        String json = "[{\"name\":\"BasicModule\"}]";

        List<ModuleInfo> modules = invokeParseModuleList(json);

        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).name()).isEqualTo("BasicModule");
        assertThat(modules.get(0).flagComponent()).isNull();
        assertThat(modules.get(0).enabledMatches()).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    private List<ModuleInfo> invokeParseModuleList(String json) throws Exception {
        ModuleService service = new ModuleService("http://localhost:8080");
        Method method = ModuleService.class.getDeclaredMethod("parseModuleList", String.class);
        method.setAccessible(true);
        return (List<ModuleInfo>) method.invoke(service, json);
    }
}
