package com.lightningfirefly.engine.internal.command;

import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import com.lightningfirefly.engine.internal.core.command.ModuleCommandResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EngineModuleEngineCommandResolverTest {

    @Mock
    private ModuleResolver moduleResolver;

    private ModuleCommandResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ModuleCommandResolver(moduleResolver);
    }

    @Test
    void resolveByName_shouldReturnCommandFromModule() {
        EngineCommand engineCommand = new TestEngineCommand("testCommand");
        EngineModule module = new TestModule(List.of(engineCommand));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));

        EngineCommand resolved = resolver.resolveByName("testCommand");

        assertThat(resolved).isSameAs(engineCommand);
    }

    @Test
    void resolveByName_withNullName_shouldReturnNull() {
        EngineCommand resolved = resolver.resolveByName(null);

        assertThat(resolved).isNull();
        verifyNoInteractions(moduleResolver);
    }

    @Test
    void resolveByName_withNonExistentName_shouldReturnNull() {
        when(moduleResolver.resolveAllModules()).thenReturn(List.of());

        EngineCommand resolved = resolver.resolveByName("nonExistent");

        assertThat(resolved).isNull();
    }

    @Test
    void resolveByName_shouldCacheCommands() {
        EngineCommand engineCommand = new TestEngineCommand("cachedCommand");
        EngineModule module = new TestModule(List.of(engineCommand));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));

        // First call populates cache
        resolver.resolveByName("cachedCommand");
        // Second call should use cache
        resolver.resolveByName("cachedCommand");

        verify(moduleResolver, times(1)).resolveAllModules();
    }

    @Test
    void resolveByName_shouldResolveFromMultipleModules() {
        EngineCommand engineCommand1 = new TestEngineCommand("cmd1");
        EngineCommand engineCommand2 = new TestEngineCommand("cmd2");
        EngineModule module1 = new TestModule(List.of(engineCommand1));
        EngineModule module2 = new TestModule(List.of(engineCommand2));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module1, module2));

        assertThat(resolver.resolveByName("cmd1")).isSameAs(engineCommand1);
        assertThat(resolver.resolveByName("cmd2")).isSameAs(engineCommand2);
    }

    @Test
    void resolveByName_withDuplicateCommandNames_shouldOverwrite() {
        EngineCommand engineCommand1 = new TestEngineCommand("duplicateName");
        EngineCommand engineCommand2 = new TestEngineCommand("duplicateName");
        EngineModule module1 = new TestModule(List.of(engineCommand1));
        EngineModule module2 = new TestModule(List.of(engineCommand2));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module1, module2));

        EngineCommand resolved = resolver.resolveByName("duplicateName");

        // Last one wins
        assertThat(resolved).isSameAs(engineCommand2);
    }

    @Test
    void invalidateCache_shouldClearCache() {
        EngineCommand engineCommand = new TestEngineCommand("cmd");
        EngineModule module = new TestModule(List.of(engineCommand));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));

        resolver.resolveByName("cmd");
        resolver.invalidateCache();
        resolver.resolveByName("cmd");

        verify(moduleResolver, times(2)).resolveAllModules();
    }

    @Test
    void invalidateCache_shouldAllowNewCommandsToBeResolved() {
        EngineCommand originalEngineCommand = new TestEngineCommand("cmd");
        EngineCommand newEngineCommand = new TestEngineCommand("newCmd");
        EngineModule originalModule = new TestModule(List.of(originalEngineCommand));
        EngineModule newModule = new TestModule(List.of(newEngineCommand));

        when(moduleResolver.resolveAllModules()).thenReturn(List.of(originalModule));
        assertThat(resolver.resolveByName("cmd")).isSameAs(originalEngineCommand);
        assertThat(resolver.resolveByName("newCmd")).isNull();

        when(moduleResolver.resolveAllModules()).thenReturn(List.of(newModule));
        resolver.invalidateCache();

        assertThat(resolver.resolveByName("newCmd")).isSameAs(newEngineCommand);
        assertThat(resolver.resolveByName("cmd")).isNull();
    }

    @Test
    void getAvailableCommandNames_shouldReturnAllNames() {
        EngineCommand engineCommand1 = new TestEngineCommand("cmd1");
        EngineCommand engineCommand2 = new TestEngineCommand("cmd2");
        EngineModule module = new TestModule(List.of(engineCommand1, engineCommand2));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));

        List<String> names = resolver.getAvailableCommandNames();

        assertThat(names).containsExactlyInAnyOrder("cmd1", "cmd2");
    }

    @Test
    void getAvailableCommandNames_withNoModules_shouldReturnEmptyList() {
        when(moduleResolver.resolveAllModules()).thenReturn(List.of());

        List<String> names = resolver.getAvailableCommandNames();

        assertThat(names).isEmpty();
    }

    @Test
    void hasCommand_shouldReturnTrueForExistingCommand() {
        EngineCommand engineCommand = new TestEngineCommand("existingCmd");
        EngineModule module = new TestModule(List.of(engineCommand));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));

        assertThat(resolver.hasCommand("existingCmd")).isTrue();
    }

    @Test
    void hasCommand_shouldReturnFalseForNonExistentCommand() {
        when(moduleResolver.resolveAllModules()).thenReturn(List.of());

        assertThat(resolver.hasCommand("nonExistent")).isFalse();
    }

    @Test
    void shouldHandleModuleWithNullCommands() {
        EngineModule module = new TestModule(null);
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));

        EngineCommand resolved = resolver.resolveByName("anyCmd");

        assertThat(resolved).isNull();
    }

    @Test
    void shouldHandleCommandWithNullName() {
        EngineCommand engineCommandWithNullName = new TestEngineCommand(null);
        EngineCommand normalEngineCommand = new TestEngineCommand("normalCmd");
        EngineModule module = new TestModule(List.of(engineCommandWithNullName, normalEngineCommand));
        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));

        assertThat(resolver.resolveByName("normalCmd")).isSameAs(normalEngineCommand);
        assertThat(resolver.getAvailableCommandNames()).containsExactly("normalCmd");
    }

    // Test implementations

    private static class TestEngineCommand implements EngineCommand {
        private final String name;

        TestEngineCommand(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, Class<?>> schema() {
            return Map.of();
        }

        @Override
        public void executeCommand(CommandPayload payload) {
            // No-op
        }
    }

    private static class TestModule implements EngineModule {
        private final List<EngineCommand> engineCommands;

        TestModule(List<EngineCommand> engineCommands) {
            this.engineCommands = engineCommands;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of();
        }

        @Override
        public List<EngineCommand> createCommands() {
            return engineCommands;
        }

        @Override
        public List<BaseComponent> createComponents() {
            return List.of();
        }

        @Override
        public BaseComponent createFlagComponent() {
            return null;
        }

        @Override
        public String getName() {
            return "TestModule";
        }
    }
}
