/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.stormstack.thunder.engine.core.container;

import ca.samanthaireland.stormstack.thunder.engine.core.match.Match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for creating matches within an ExecutionContainer.
 *
 * <p>Example usage:
 * <pre>{@code
 * Match match = container.match()
 *     .withModules("EntityModule", "RigidBodyModule")
 *     .create();
 * }</pre>
 */
public final class MatchBuilder {

    private final ExecutionContainer container;
    private final List<String> moduleNames = new ArrayList<>();

    /**
     * Creates a new MatchBuilder for the given container.
     *
     * @param container the container to create matches in
     */
    public MatchBuilder(ExecutionContainer container) {
        this.container = container;
    }

    /**
     * Adds a module to be enabled for this match.
     *
     * @param moduleName the module name
     * @return this builder for chaining
     */
    public MatchBuilder withModule(String moduleName) {
        this.moduleNames.add(moduleName);
        return this;
    }

    /**
     * Adds multiple modules to be enabled for this match.
     *
     * @param moduleNames the module names
     * @return this builder for chaining
     */
    public MatchBuilder withModules(String... moduleNames) {
        this.moduleNames.addAll(Arrays.asList(moduleNames));
        return this;
    }

    /**
     * Creates the match in the container.
     *
     * @return the created match with assigned ID
     */
    public Match create() {
        Match match = new Match(
                0L, // ID will be assigned by the container
                container.getId(),
                List.copyOf(moduleNames)
        );
        return container.matches().create(match);
    }
}
