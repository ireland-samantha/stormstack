/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.game.domain;

import java.util.Map;

/**
 * Represents a command that can be executed by an AI.
 *
 * <p>Commands are sent to the server for execution within a container.
 * Each command has a name and a payload of parameters.
 */
public interface AICommand {

    /**
     * Get the command name.
     *
     * @return the command name (e.g., "spawn", "move", "attack")
     */
    String commandName();

    /**
     * Get the command payload.
     *
     * @return the payload as a map
     */
    Map<String, Object> payload();

    /**
     * Create a simple command with no payload.
     */
    static AICommand of(String commandName) {
        return new SimpleAICommand(commandName, Map.of());
    }

    /**
     * Create a command with payload.
     */
    static AICommand of(String commandName, Map<String, Object> payload) {
        return new SimpleAICommand(commandName, payload);
    }

    /**
     * Simple implementation of AICommand.
     */
    record SimpleAICommand(String commandName, Map<String, Object> payload) implements AICommand {}
}
