package com.lightningfirefly.engine.internal.ext;

import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.internal.ext.module.ModuleFactoryFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EngineModuleFactoryFileLoaderTest {

    @TempDir
    Path tempDir;

    private ModuleFactoryFileLoader moduleFactoryFileLoader;

    @BeforeEach
    void setUp() {
        moduleFactoryFileLoader = new ModuleFactoryFileLoader();
    }

    @Test
    void loadModuleFactories_withNonExistentFile_shouldReturnEmptyList() throws IOException {
        File nonExistent = new File(tempDir.toFile(), "nonexistent.jar");

        List<ModuleFactory> result = moduleFactoryFileLoader.loadModuleFactories(nonExistent);

        assertThat(result).isEmpty();
    }

    @Test
    void loadModuleFactories_withNonJarFile_shouldReturnEmptyList() throws IOException {
        Path textFile = tempDir.resolve("readme.txt");
        Files.createFile(textFile);

        List<ModuleFactory> result = moduleFactoryFileLoader.loadModuleFactories(textFile.toFile());

        assertThat(result).isEmpty();
    }

    @Test
    void loadModuleFactories_withDirectory_shouldReturnEmptyList() throws IOException {
        Path subDir = tempDir.resolve("subdir.jar"); // Named .jar but is a directory
        Files.createDirectory(subDir);

        List<ModuleFactory> result = moduleFactoryFileLoader.loadModuleFactories(subDir.toFile());

        assertThat(result).isEmpty();
    }

    @Test
    void getModuleName_withFactorySuffix_shouldRemoveSuffix() {
        TestModuleFactory factory = new TestModuleFactory();

        String result = moduleFactoryFileLoader.getModuleName(factory);

        assertThat(result).isEqualTo("TestModule");
    }

    @Test
    void getModuleName_withoutFactorySuffix_shouldReturnFullName() {
        TestProcessor processor = new TestProcessor();

        String result = moduleFactoryFileLoader.getModuleName(processor);

        assertThat(result).isEqualTo("TestProcessor");
    }

    @Test
    void getModuleName_withJustFactory_shouldReturnEmptyString() {
        Factory factory = new Factory();

        String result = moduleFactoryFileLoader.getModuleName(factory);

        assertThat(result).isEmpty();
    }

    @Test
    void getModuleName_withFactoryInMiddle_shouldNotRemoveSuffix() {
        FactoryManager manager = new FactoryManager();

        String result = moduleFactoryFileLoader.getModuleName(manager);

        // "FactoryManager" doesn't end with "Factory", so full name returned
        assertThat(result).isEqualTo("FactoryManager");
    }

    // Test helper classes
    static class TestModuleFactory implements ModuleFactory {
        @Override
        public EngineModule create(ModuleContext context) {
            return null;
        }
    }

    static class TestProcessor implements ModuleFactory {
        @Override
        public EngineModule create(ModuleContext context) {
            return null;
        }
    }

    static class Factory implements ModuleFactory {
        @Override
        public EngineModule create(ModuleContext context) {
            return null;
        }
    }

    static class FactoryManager implements ModuleFactory {
        @Override
        public EngineModule create(ModuleContext context) {
            return null;
        }
    }
}
