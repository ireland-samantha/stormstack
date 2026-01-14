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

package ca.samanthaireland.game.orchestrator;

import java.io.IOException;
import java.util.List;

/**
 * Operations for managing matches within a container.
 * Follows Interface Segregation Principle - focused interface for match operations only.
 */
public interface MatchOperations {

    /**
     * Match creation response.
     */
    record MatchResponse(long id, List<String> enabledModules, List<String> enabledAIs) {}

    /**
     * Create a match with the specified modules and AIs.
     *
     * @param modules list of module names to enable
     * @param ais list of AI names to enable
     * @return the created match response
     * @throws IOException if creation fails
     */
    MatchResponse createMatch(List<String> modules, List<String> ais) throws IOException;

    /**
     * Delete a match.
     *
     * @param matchId the match ID
     * @return true if deleted
     * @throws IOException if deletion fails
     */
    boolean deleteMatch(long matchId) throws IOException;
}
