package com.lightningfirefly.engine.internal.command;

import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.internal.core.command.InMemoryCommandQueueManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEngineCommandManagerTest {

    private InMemoryCommandQueueManager commandManager;

    @BeforeEach
    void setUp() {
        commandManager = new InMemoryCommandQueueManager();
    }

    @Test
    void schedule_shouldAddCommandToQueue() {
        EngineCommand command = new TestEngineCommand("test");
        TestPayload payload = new TestPayload("data");

        commandManager.enqueue(command, payload);

        assertThat(commandManager.getQueueSize()).isEqualTo(1);
    }

    @Test
    void schedule_withNullCommand_shouldNotAddToQueue() {
        commandManager.enqueue(null, new TestPayload("data"));

        assertThat(commandManager.getQueueSize()).isEqualTo(0);
    }

    @Test
    void schedule_withNullPayload_shouldAddToQueue() {
        EngineCommand command = new TestEngineCommand("test");

        commandManager.enqueue(command, null);

        assertThat(commandManager.getQueueSize()).isEqualTo(1);
    }

    @Test
    void executeCommands_shouldExecuteSpecifiedAmount() {
        List<String> executedCommands = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            commandManager.enqueue(new RecordingEngineCommand("cmd" + i, executedCommands), new TestPayload("data"));
        }

        commandManager.executeCommands(3);

        assertThat(executedCommands).containsExactly("cmd0", "cmd1", "cmd2");
        assertThat(commandManager.getQueueSize()).isEqualTo(2);
    }

    @Test
    void executeCommands_shouldExecuteAllWhenAmountExceedsQueueSize() {
        List<String> executedCommands = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            commandManager.enqueue(new RecordingEngineCommand("cmd" + i, executedCommands), new TestPayload("data"));
        }

        commandManager.executeCommands(10);

        assertThat(executedCommands).containsExactly("cmd0", "cmd1", "cmd2");
        assertThat(commandManager.getQueueSize()).isEqualTo(0);
    }

    @Test
    void executeCommands_withZeroAmount_shouldNotExecuteAny() {
        List<String> executedCommands = new ArrayList<>();
        commandManager.enqueue(new RecordingEngineCommand("cmd", executedCommands), new TestPayload("data"));

        commandManager.executeCommands(0);

        assertThat(executedCommands).isEmpty();
        assertThat(commandManager.getQueueSize()).isEqualTo(1);
    }

    @Test
    void executeCommands_withNegativeAmount_shouldNotExecuteAny() {
        List<String> executedCommands = new ArrayList<>();
        commandManager.enqueue(new RecordingEngineCommand("cmd", executedCommands), new TestPayload("data"));

        commandManager.executeCommands(-1);

        assertThat(executedCommands).isEmpty();
        assertThat(commandManager.getQueueSize()).isEqualTo(1);
    }

    @Test
    void executeCommands_shouldPassPayloadToCommand() {
        TestPayload payload = new TestPayload("test-data");
        PayloadCapturingEngineCommand command = new PayloadCapturingEngineCommand("capture");
        commandManager.enqueue(command, payload);

        commandManager.executeCommands(1);

        Assertions.assertThat(command.getCapturedPayload()).isSameAs(payload);
    }

    @Test
    void executeCommands_shouldContinueOnCommandFailure() {
        List<String> executedCommands = new ArrayList<>();
        commandManager.enqueue(new RecordingEngineCommand("cmd1", executedCommands), new TestPayload("data"));
        commandManager.enqueue(new FailingEngineCommand("failing"), new TestPayload("data"));
        commandManager.enqueue(new RecordingEngineCommand("cmd2", executedCommands), new TestPayload("data"));

        commandManager.executeCommands(3);

        assertThat(executedCommands).containsExactly("cmd1", "cmd2");
        assertThat(commandManager.getQueueSize()).isEqualTo(0);
    }

    @Test
    void executeCommands_onEmptyQueue_shouldDoNothing() {
        commandManager.executeCommands(5);

        assertThat(commandManager.getQueueSize()).isEqualTo(0);
    }

    @Test
    void clear_shouldRemoveAllCommands() {
        for (int i = 0; i < 5; i++) {
            commandManager.enqueue(new TestEngineCommand("cmd" + i), new TestPayload("data"));
        }

        commandManager.clear();

        assertThat(commandManager.getQueueSize()).isEqualTo(0);
    }

    @Test
    void getQueueSize_shouldReturnCorrectSize() {
        assertThat(commandManager.getQueueSize()).isEqualTo(0);

        commandManager.enqueue(new TestEngineCommand("cmd1"), new TestPayload("data"));
        assertThat(commandManager.getQueueSize()).isEqualTo(1);

        commandManager.enqueue(new TestEngineCommand("cmd2"), new TestPayload("data"));
        assertThat(commandManager.getQueueSize()).isEqualTo(2);
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        int numThreads = 10;
        int commandsPerThread = 100;
        List<String> executedCommands = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch scheduleLatch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Schedule commands from multiple threads
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < commandsPerThread; i++) {
                    commandManager.enqueue(
                            new RecordingEngineCommand("t" + threadId + "-cmd" + i, executedCommands),
                            new TestPayload("data")
                    );
                }
                scheduleLatch.countDown();
            });
        }

        scheduleLatch.await(5, TimeUnit.SECONDS);
        assertThat(commandManager.getQueueSize()).isEqualTo(numThreads * commandsPerThread);

        // Execute all commands
        commandManager.executeCommands(numThreads * commandsPerThread);

        assertThat(executedCommands).hasSize(numThreads * commandsPerThread);
        assertThat(commandManager.getQueueSize()).isEqualTo(0);

        executor.shutdown();
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

    private static class RecordingEngineCommand implements EngineCommand {
        private final String name;
        private final List<String> executedList;

        RecordingEngineCommand(String name, List<String> executedList) {
            this.name = name;
            this.executedList = executedList;
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
            executedList.add(name);
        }
    }

    private static class PayloadCapturingEngineCommand implements EngineCommand {
        private final String name;
        private CommandPayload capturedPayload;

        PayloadCapturingEngineCommand(String name) {
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
            this.capturedPayload = payload;
        }

        public CommandPayload getCapturedPayload() {
            return capturedPayload;
        }
    }

    private static class FailingEngineCommand implements EngineCommand {
        private final String name;

        FailingEngineCommand(String name) {
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
            throw new RuntimeException("Command failed intentionally");
        }
    }

    private record TestPayload(String data) implements CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return Map.of("key", data);
        }
    }
}
