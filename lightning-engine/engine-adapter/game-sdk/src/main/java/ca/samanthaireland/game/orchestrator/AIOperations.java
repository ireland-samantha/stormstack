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
 * Operations for managing AI within a container.
 * Follows Interface Segregation Principle - focused interface for AI operations only.
 */
public interface AIOperations {

    /**
     * Check if an AI is available.
     *
     * @param aiName the AI name
     * @return true if the AI is available
     * @throws IOException if the check fails
     */
    boolean hasAI(String aiName) throws IOException;

    /**
     * List all available AI.
     *
     * @return list of AI names
     * @throws IOException if listing fails
     */
    List<String> listAI() throws IOException;

    /**
     * Upload an AI JAR file.
     *
     * @param fileName the JAR file name
     * @param data the JAR file data
     * @throws IOException if upload fails
     */
    void uploadAI(String fileName, byte[] data) throws IOException;

    /**
     * Uninstall an AI.
     *
     * @param aiName the AI name
     * @throws IOException if uninstall fails
     */
    void uninstallAI(String aiName) throws IOException;
}
