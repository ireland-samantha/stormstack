# Adding a New Service to StormStack Thunder

This guide documents how to add a new Quarkus-based microservice to the StormStack Thunder project, following the established patterns from `thunder-auth` and `thunder-control-plane`.

## Architecture Overview

StormStack Thunder follows a **two-module pattern** for services:

```
thunder-<service>-core/      # Pure domain (NO framework dependencies)
├── model/                     # Domain models, value objects
├── repository/                # Repository interfaces
├── service/                   # Service interfaces + implementations
├── config/                    # Configuration interfaces
└── exception/                 # Domain exceptions

thunder-<service>/           # Quarkus provider (framework integration)
└── provider/
    ├── config/                # QuarkusConfig classes + ServiceProducer
    ├── http/                  # REST resources
    ├── persistence/           # MongoDB/Redis implementations
    ├── dto/                   # HTTP request/response DTOs
    └── startup/               # Bootstrap logic
```

This separation ensures:
- Core business logic has no framework dependencies
- Services can be tested in isolation
- Framework can be swapped without touching business logic
- Clean architecture boundaries are enforced

---

## Step-by-Step Guide

### 1. Create the Core Module

#### 1.1 Create Maven Module

Create `thunder-<service>-core/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>ca.samanthaireland</groupId>
        <artifactId>backend</artifactId>
        <version>0.0.3-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>thunder-<service>-core</artifactId>
    <name>Lightning <Service> Core</name>
    <description>Core domain for Lightning <Service> - NO framework dependencies</description>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- ONLY standard Java libraries and pure utility libs -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

#### 1.2 Create Core Package Structure

```
thunder-<service>-core/src/main/java/ca/samanthaireland/lightning/<service>/
├── config/
│   └── <Service>Configuration.java       # Configuration interface
├── model/
│   ├── <Entity>.java                      # Domain models
│   └── <Entity>Id.java                    # Strongly-typed IDs
├── repository/
│   └── <Entity>Repository.java            # Repository interfaces
├── service/
│   ├── <Entity>Service.java               # Service interface
│   └── <Entity>ServiceImpl.java           # Service implementation
└── exception/
    └── <Service>Exception.java            # Base exception
```

#### 1.3 Configuration Interface

```java
// config/<Service>Configuration.java
package ca.samanthaireland.stormstack.thunder.<service>.config;

/**
 * Configuration interface for <Service>.
 * NO framework annotations - pure Java interface.
 */
public interface <Service>Configuration {
    String someProperty();
    int anotherProperty();
}
```

#### 1.4 Repository Interface

```java
// repository/<Entity>Repository.java
package ca.samanthaireland.stormstack.thunder.<service>.repository;

import java.util.Optional;

/**
 * Repository for <Entity> persistence.
 * NO framework annotations.
 */
public interface <Entity>Repository {
    <Entity> save(<Entity> entity);
    Optional<<Entity>> findById(<Entity>Id id);
    void delete(<Entity>Id id);
}
```

#### 1.5 Service Implementation

```java
// service/<Entity>ServiceImpl.java
package ca.samanthaireland.stormstack.thunder.<service>.service;

// NO jakarta.inject.*, NO io.quarkus.* imports!

/**
 * Implementation of <Entity>Service.
 * NO framework annotations - constructor injection only.
 */
public class <Entity>ServiceImpl implements <Entity>Service {
    private final <Entity>Repository repository;
    private final <Service>Configuration config;

    // Plain constructor - no @Inject
    public <Entity>ServiceImpl(
            <Entity>Repository repository,
            <Service>Configuration config) {
        this.repository = repository;
        this.config = config;
    }

    @Override
    public <Entity> create(Create<Entity>Request request) {
        // Business logic here
    }
}
```

---

### 2. Create the Provider Module

#### 2.1 Create Maven Module

Create `thunder-<service>/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>ca.samanthaireland</groupId>
        <artifactId>backend</artifactId>
        <version>0.0.3-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>thunder-<service></artifactId>
    <name>Lightning <Service></name>
    <description><Service> microservice - Quarkus-based REST API</description>

    <properties>
        <quarkus.platform.version>3.30.3</quarkus.platform.version>
        <hibernate-validator.version>9.0.0.Final</hibernate-validator.version>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Core module -->
        <dependency>
            <groupId>ca.samanthaireland</groupId>
            <artifactId>thunder-<service>-core</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Quarkus core -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>

        <!-- REST -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-jackson</artifactId>
        </dependency>

        <!-- Choose persistence: MongoDB or Redis -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-mongodb-client</artifactId>
        </dependency>
        <!-- OR -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-redis-client</artifactId>
        </dependency>

        <!-- Health checks -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-health</artifactId>
        </dependency>

        <!-- Metrics -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Bean Validation (Java 25 compatible) -->
        <dependency>
            <groupId>org.hibernate.validator</groupId>
            <artifactId>hibernate-validator</artifactId>
            <version>${hibernate-validator.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-validator</artifactId>
        </dependency>

        <!-- Auth integration (if needed) -->
        <dependency>
            <groupId>ca.samanthaireland</groupId>
            <artifactId>thunder-auth-adapter-quarkus-provider</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>25</release>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.42</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>--add-opens java.base/java.lang=ALL-UNNAMED</argLine>
                    <systemPropertyVariables>
                        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <!-- Docker build profile -->
        <profile>
            <id>docker</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.codehaus.mojo</groupId>
                        <artifactId>exec-maven-plugin</artifactId>
                        <version>3.5.0</version>
                        <executions>
                            <execution>
                                <id>build-docker-image</id>
                                <phase>install</phase>
                                <goals>
                                    <goal>exec</goal>
                                </goals>
                                <configuration>
                                    <executable>docker</executable>
                                    <workingDirectory>${project.basedir}/..</workingDirectory>
                                    <arguments>
                                        <argument>build</argument>
                                        <argument>-f</argument>
                                        <argument>thunder-<service>/Dockerfile.prebuilt</argument>
                                        <argument>-t</argument>
                                        <argument>samanthacireland/thunder-<service>:${project.version}</argument>
                                        <argument>-t</argument>
                                        <argument>samanthacireland/thunder-<service>:latest</argument>
                                        <argument>.</argument>
                                    </arguments>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>

        <!-- Local Docker Registry profile -->
        <profile>
            <id>localDockerRegistry</id>
            <properties>
                <local.registry.host>${env.TAILSCALE_IP}</local.registry.host>
                <local.registry.port>5001</local.registry.port>
                <local.registry.image>${local.registry.host}:${local.registry.port}/thunder-<service>:${project.version}</local.registry.image>
            </properties>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.codehaus.mojo</groupId>
                        <artifactId>exec-maven-plugin</artifactId>
                        <version>3.5.0</version>
                        <executions>
                            <execution>
                                <id>build-push-multiplatform-image</id>
                                <phase>install</phase>
                                <goals>
                                    <goal>exec</goal>
                                </goals>
                                <configuration>
                                    <executable>docker</executable>
                                    <workingDirectory>${project.basedir}/..</workingDirectory>
                                    <arguments>
                                        <argument>buildx</argument>
                                        <argument>build</argument>
                                        <argument>--platform</argument>
                                        <argument>linux/amd64,linux/arm64</argument>
                                        <argument>-f</argument>
                                        <argument>thunder-<service>/Dockerfile.prebuilt</argument>
                                        <argument>-t</argument>
                                        <argument>${local.registry.image}</argument>
                                        <argument>--push</argument>
                                        <argument>.</argument>
                                    </arguments>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

#### 2.2 Create Provider Package Structure

```
thunder-<service>/src/main/java/ca/samanthaireland/lightning/<service>/provider/
├── config/
│   ├── Quarkus<Service>Config.java    # @ConfigMapping implementation
│   └── ServiceProducer.java           # CDI producers for core services
├── dto/
│   ├── Create<Entity>Request.java     # HTTP request DTOs
│   └── <Entity>Response.java          # HTTP response DTOs
├── http/
│   ├── <Entity>Resource.java          # REST endpoints
│   └── ExceptionMappers.java          # Exception to HTTP response mapping
├── persistence/
│   └── Mongo<Entity>Repository.java   # MongoDB implementation
└── startup/
    └── <Service>Bootstrap.java        # Initialization logic
```

#### 2.3 Quarkus Configuration Mapping

```java
// config/Quarkus<Service>Config.java
package ca.samanthaireland.stormstack.thunder.<service>.provider.config;

import ca.samanthaireland.stormstack.thunder.<service>.config.<Service>Configuration;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "<service>")
public interface Quarkus<Service>Config extends <Service>Configuration {

    @Override
    @WithDefault("default-value")
    String someProperty();

    @Override
    @WithDefault("10")
    int anotherProperty();
}
```

#### 2.4 Service Producer (The Bridge)

```java
// config/ServiceProducer.java
package ca.samanthaireland.stormstack.thunder.<service>.provider.config;

import ca.samanthaireland.stormstack.thunder.<service>.config.<Service>Configuration;
import ca.samanthaireland.stormstack.thunder.<service>.repository.<Entity>Repository;
import ca.samanthaireland.stormstack.thunder.<service>.service.<Entity>Service;
import ca.samanthaireland.stormstack.thunder.<service>.service.<Entity>ServiceImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Quarkus CDI producer for core domain services.
 *
 * This bridges framework-agnostic core services with Quarkus CDI.
 */
@ApplicationScoped
public class ServiceProducer {

    @Produces
    @Singleton
    public <Service>Configuration configuration(Quarkus<Service>Config quarkusConfig) {
        return quarkusConfig;
    }

    @Produces
    @Singleton
    public <Entity>Service entityService(
            <Entity>Repository repository,
            <Service>Configuration config) {
        return new <Entity>ServiceImpl(repository, config);
    }
}
```

#### 2.5 REST Resource

```java
// http/<Entity>Resource.java
package ca.samanthaireland.stormstack.thunder.<service>.provider.http;

import ca.samanthaireland.stormstack.thunder.<service>.service.<Entity>Service;
import ca.samanthaireland.stormstack.thunder.<service>.provider.dto.*;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/api/<entities>")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class <Entity>Resource {

    private final <Entity>Service service;

    @Inject
    public <Entity>Resource(<Entity>Service service) {
        this.service = service;
    }

    @POST
    public Response create(@Valid Create<Entity>Request request) {
        var entity = service.create(request);
        return Response.created(URI.create("/api/<entities>/" + entity.getId()))
                .entity(<Entity>Response.from(entity))
                .build();
    }

    @GET
    @Path("/{id}")
    public <Entity>Response getById(@PathParam("id") String id) {
        return service.findById(new <Entity>Id(id))
                .map(<Entity>Response::from)
                .orElseThrow(() -> new <Entity>NotFoundException(id));
    }
}
```

---

### 3. Create Dockerfile

Create `thunder-<service>/Dockerfile.prebuilt`:

```dockerfile
# Lightning <Service> - Prebuilt Dockerfile
# Expects JAR to be pre-built via: mvn package -DskipTests -pl thunder-<service>
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S lightning && adduser -S lightning -G lightning

# Copy the Quarkus fast-jar structure
COPY thunder-<service>/target/quarkus-app/lib/ /app/lib/
COPY thunder-<service>/target/quarkus-app/*.jar /app/
COPY thunder-<service>/target/quarkus-app/app/ /app/app/
COPY thunder-<service>/target/quarkus-app/quarkus/ /app/quarkus/

# Set ownership
RUN chown -R lightning:lightning /app

USER lightning

# Expose service port (choose unique port: 8083, 8084, etc.)
EXPOSE 808X

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:808X/q/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
```

---

### 4. Create Application Configuration

Create `thunder-<service>/src/main/resources/application.yml`:

```yaml
quarkus:
  http:
    port: 808X  # Choose unique port

  # MongoDB (if using)
  mongodb:
    connection-string: ${MONGODB_URI:mongodb://localhost:27017}
    database: thunder-<service>

  # Redis (if using)
  redis:
    hosts: ${REDIS_HOSTS:redis://localhost:6379}

  # Health and metrics
  smallrye-health:
    root-path: /q/health
  micrometer:
    export:
      prometheus:
        path: /q/metrics

# Service configuration
<service>:
  some-property: ${SOME_PROPERTY:default-value}
  another-property: ${ANOTHER_PROPERTY:10}
```

---

### 5. Update Root POM

Add modules to the root `pom.xml`:

```xml
<modules>
    <!-- Existing modules -->
    <module>thunder-<service>-core</module>
    <module>thunder-<service></module>
</modules>
```

---

### 6. Update Docker Compose

Add service to `docker-compose.yml`:

```yaml
services:
  # ... existing services ...

  <service>:
    image: ${<SERVICE>_IMAGE:-samanthacireland/thunder-<service>:0.0.3-SNAPSHOT}
    container_name: thunder-<service>
    ports:
      - "808X:808X"
    environment:
      - JAVA_OPTS=-Dquarkus.http.host=0.0.0.0 -Xms128m -Xmx256m
      - QUARKUS_LOG_LEVEL=INFO
      - MONGODB_URI=mongodb://mongodb:27017  # If using MongoDB
      - REDIS_HOSTS=redis://redis:6379        # If using Redis
      # Auth integration
      - AUTH_SERVICE_URL=http://auth:8082
    depends_on:
      mongodb:  # Or redis
        condition: service_healthy
      auth:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:808X/q/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 15s
    restart: unless-stopped
    networks:
      - lightning-network
```

---

### 7. Update Build Script

Add to `build.sh`:

```bash
DOCKER_IMAGE_<SERVICE>="samanthacireland/thunder-<service>"

do_docker() {
    # ... existing builds ...

    # Build Lightning <Service>
    echo "Building thunder-<service>..."
    docker build -f thunder-<service>/Dockerfile.prebuilt \
        -t "${DOCKER_IMAGE_<SERVICE>}:${DOCKER_TAG}" \
        -t "${DOCKER_IMAGE_<SERVICE>}:${VERSION}" \
        .
    echo "  Built: ${DOCKER_IMAGE_<SERVICE>}:${DOCKER_TAG}"
}

do_docker_local() {
    # ... existing pushes ...

    echo "Building and pushing thunder-<service>..."
    mvn install -PlocalDockerRegistry -pl thunder-<service> -DskipTests
}
```

---

### 8. Update Environment Template

Add to `.env.example`:

```bash
# =============================================================================
# <Service> Configuration
# =============================================================================

# <SERVICE>_IMAGE=samanthacireland/thunder-<service>:0.0.3-SNAPSHOT
# <SERVICE>_SOME_PROPERTY=value
```

---

## Checklist

Before declaring the service complete:

- [ ] Core module has NO framework dependencies
- [ ] All services use constructor injection (no `@Inject` in core)
- [ ] ServiceProducer bridges core to Quarkus CDI
- [ ] REST resources have proper validation and error handling
- [ ] Dockerfile builds successfully
- [ ] Health check endpoint works (`/q/health`)
- [ ] Metrics endpoint works (`/q/metrics`)
- [ ] Service added to docker-compose.yml
- [ ] Service added to build.sh
- [ ] Unit tests for core services
- [ ] Integration tests for REST endpoints
- [ ] `./build.sh docker` builds all images
- [ ] `docker compose up` starts all services

---

## Example Services

For reference implementations, see:

- **thunder-auth**: JWT authentication, user management, role-based access
  - Core: `thunder-auth-core/`
  - Provider: `thunder-auth/`
  - Port: 8082

- **thunder-control-plane**: Cluster management, node registry, autoscaling
  - Core: `thunder-control-plane-core/`
  - Provider: `thunder-control-plane/`
  - Port: 8081
