package com.lightningfirefly.engine.api.resource;

//import com.lightningfirefly.engine.core.resources.Resource;
import com.lightningfirefly.engine.rendering.render2d.Sprite;

import java.io.Closeable;

// reads a resource from the engine server and fetches it if not existing
public interface ResourceReader extends Closeable {
    // todo ai: using the decorator pattern implement a cached resourced reader and a default resource
    // reader that parses out the appropriate RenderComponent with caching
    Sprite getSprite(long resourceId);
}
