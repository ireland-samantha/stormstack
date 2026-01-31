/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Test resource that starts a MongoDB container for integration tests.
 */
public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

    private static final MongoDBContainer MONGO_CONTAINER =
            new MongoDBContainer(DockerImageName.parse("mongo:6.0"));

    @Override
    public Map<String, String> start() {
        MONGO_CONTAINER.start();
        return Map.of(
                "quarkus.mongodb.connection-string", MONGO_CONTAINER.getConnectionString()
        );
    }

    @Override
    public void stop() {
        MONGO_CONTAINER.stop();
    }
}
