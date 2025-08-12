package com.lightningfirefly.engine.core.command;

import java.util.Map;
import java.util.function.Consumer;

public interface CommandBuilder {
    static CommandBuilder newCommand() {
        return new DefaultCommandBuilder();
    }

    CommandBuilder withName(String name);
    CommandBuilder withSchema(Map<String, Class<?>> schema);
    CommandBuilder withExecution(Consumer<CommandPayload> execution);
    EngineCommand build();


    class DefaultCommandBuilder implements CommandBuilder {

        private String name;
        private Map<String, Class<?>> schema;
        private Consumer<CommandPayload> execution;

        @Override
        public CommandBuilder withName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public CommandBuilder withSchema(Map<String, Class<?>> schema) {
            this.schema = schema;
            return this;
        }

        @Override
        public CommandBuilder withExecution(Consumer<CommandPayload> execution) {
            this.execution = execution;
            return this;
        }

        @Override
        public EngineCommand build() {
            return new EngineCommand() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public Map<String, Class<?>> schema() {
                    return schema;
                }

                @Override
                public void executeCommand(CommandPayload payload) {
                    execution.accept(payload);
                }
            };
        }
    }
}
