/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;

/**
 * Test resource that starts a MongoDB container for integration tests.
 * If MongoDB is already available at localhost:27017, it uses that instead.
 */
public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

    private static final int DEFAULT_PORT = 27017;
    private static final String[] MONGO_HOSTS = {
            "localhost",
            "172.17.0.2",  // Docker bridge network default
            "host.docker.internal",
            "mongo"
    };

    private MongoDBContainer mongoContainer;
    private boolean usingExternalMongo = false;

    @Override
    public Map<String, String> start() {
        for (String host : MONGO_HOSTS) {
            if (isMongoAvailable(host, DEFAULT_PORT)) {
                usingExternalMongo = true;
                return Map.of(
                        "quarkus.mongodb.connection-string", "mongodb://" + host + ":" + DEFAULT_PORT
                );
            }
        }

        mongoContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0"));
        mongoContainer.start();
        return Map.of(
                "quarkus.mongodb.connection-string", mongoContainer.getConnectionString()
        );
    }

    @Override
    public void stop() {
        if (!usingExternalMongo && mongoContainer != null) {
            mongoContainer.stop();
        }
    }

    private static boolean isMongoAvailable(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
