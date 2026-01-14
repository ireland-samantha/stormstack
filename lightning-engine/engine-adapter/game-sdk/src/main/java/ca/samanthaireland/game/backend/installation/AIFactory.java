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


package ca.samanthaireland.game.backend.installation;

import ca.samanthaireland.game.domain.AI;
import ca.samanthaireland.game.domain.AIContext;

/**
 * Factory interface for creating AI instances.
 *
 * <p>Each AI implementation should provide a factory that implements this interface.
 * The factory is responsible for creating configured instances of the AI.
 *
 * <p>AI instances are called every game tick via {@link AI#onTick()} and can
 * be used to implement game logic that runs continuously.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class MyAIFactory implements AIFactory {
 *     @Override
 *     public AI create(AIContext context) {
 *         return new MyAI(context);
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "MyAI";
 *     }
 * }
 * }</pre>
 *
 * @see AI
 * @see AIContext
 */
public interface AIFactory {

    /**
     * Create an instance of the AI.
     *
     * @param context the context for dependency injection
     * @return the created AI instance
     */
    AI create(AIContext context);

    /**
     * Get the unique name for this AI.
     *
     * <p>This name is used for identification in the UI and REST API.
     *
     * @return the AI name
     */
    String getName();
}
