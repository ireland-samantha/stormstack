# Lightning Engine Security Best Practices

Project-specific security guidelines for Lightning Engine development.

## Architecture-Specific Security

### 1. Multi-Container Isolation

**Threat**: Malicious game module escapes container and affects other matches.

**Best Practices**:
- Each `ExecutionContainer` must have isolated `ContainerClassLoader`
- ClassLoader parent must be bootstrap, not system
- Implement `SecurityManager` to restrict file I/O, network, reflection per container
- Validate container boundaries on every ECS component access
- Entity IDs must be scoped to container (use container UUID prefix)

**Example**:
```java
// SECURE: Scoped entity access
public float getComponent(ContainerId container, EntityId entity, String component) {
    if (!entity.belongsTo(container)) {
        throw new SecurityException("Cross-container access denied");
    }
    return store.getFloat(entity, component);
}

// INSECURE: No boundary check
public float getComponent(EntityId entity, String component) {
    return store.getFloat(entity, component); // Can access any entity
}
```

### 2. Module Hot-Loading Security

**Threat**: Malicious JAR uploaded as game module executes arbitrary code.

**Best Practices**:
- Require JAR signature verification before loading
- Maintain whitelist of approved module developers (signing certificates)
- Scan JARs for known malware patterns before loading
- Limit module ClassLoader permissions (no network, file system access)
- Module manifest must declare required permissions
- Reject modules requesting dangerous permissions without admin approval

**Example**:
```java
// SECURE: Signature verification
public void installModule(ContainerId id, byte[] jarBytes) {
    if (!JarSignatureValidator.verify(jarBytes, trustedCertificates)) {
        throw new SecurityException("Module signature invalid");
    }

    ModuleManifest manifest = parseManifest(jarBytes);
    if (manifest.requiresDangerousPermissions() && !currentUser.isAdmin()) {
        throw new SecurityException("Admin approval required");
    }

    container.modules().install(jarBytes);
}
```

### 3. Command Queue Security

**Threat**: Player sends malicious command that crashes server or affects other players.

**Best Practices**:
- Every command handler MUST validate input using `@Valid` or manual checks
- Commands must include origin player/session ID
- Verify command sender has permission to execute (owns entity, in match, etc.)
- Rate limit commands per player (e.g., 100 commands/second)
- Validate entity ownership before allowing state changes
- Log suspicious command patterns (too many, invalid targets)

**Example**:
```java
// SECURE: Full validation
@Override
public void handle(MoveCommand cmd, CommandContext ctx) {
    // Validate input
    if (cmd.entityId() == null || cmd.x() < 0 || cmd.y() < 0) {
        throw new ValidationException("Invalid command parameters");
    }

    // Verify ownership
    Entity entity = ctx.getEntity(cmd.entityId());
    if (!entity.isOwnedBy(ctx.getPlayerId())) {
        throw new SecurityException("Cannot move other player's entity");
    }

    // Check rate limit
    if (ctx.getRateLimiter().isExceeded(ctx.getPlayerId())) {
        throw new RateLimitException("Too many commands");
    }

    // Execute
    entity.setPosition(cmd.x(), cmd.y());
}
```

### 4. WebSocket Security

**Threat**: Unauthenticated connection streams game state to attacker.

**Best Practices**:
- ALL WebSocket endpoints MUST authenticate in `@OnOpen`
- Validate JWT token before allowing connection
- Verify user has permission to watch this match
- Track sessions per match, limit concurrent connections per user
- Validate ALL incoming `@OnMessage` payloads
- Sanitize snapshot data before sending (don't leak hidden entities)

**Example**:
```java
// SECURE: Authentication required
@ServerEndpoint("/ws/snapshots/{matchId}")
public class SnapshotWebSocket {

    @OnOpen
    public void onOpen(Session session, @PathParam("matchId") UUID matchId) {
        String token = session.getRequestParameterMap().get("token").get(0);

        Claims claims = jwtValidator.validate(token);
        String userId = claims.getSubject();

        if (!matchService.isPlayerInMatch(matchId, userId)) {
            session.close(new CloseReason(
                CloseReason.CloseCodes.VIOLATED_POLICY,
                "Not authorized for this match"
            ));
            return;
        }

        snapshotService.subscribe(matchId, session, userId);
    }
}
```

### 5. Resource Upload Security

**Threat**: Path traversal attack overwrites server files or uploads malware.

**Best Practices**:
- Validate filename doesn't contain `..`, `/`, `\`
- Use `Path.normalize()` and verify result is within allowed directory
- Enforce file size limits (e.g., 10MB max)
- Validate file type by magic bytes, not extension
- Scan uploaded files with antivirus if available
- Store with random UUID filename, not user-provided name
- Restrict file permissions (no execute)

**Example**:
```java
// SECURE: Full validation
public ResourceId uploadResource(String filename, byte[] data) {
    // Validate filename
    if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
        throw new SecurityException("Invalid filename");
    }

    // Validate size
    if (data.length > MAX_RESOURCE_SIZE) {
        throw new ValidationException("File too large");
    }

    // Validate file type
    String mimeType = FileTypeDetector.detect(data);
    if (!ALLOWED_TYPES.contains(mimeType)) {
        throw new ValidationException("File type not allowed: " + mimeType);
    }

    // Generate safe filename
    ResourceId id = ResourceId.generate();
    Path targetPath = resourcesDir.resolve(id.value().toString());

    // Verify within bounds
    if (!targetPath.normalize().startsWith(resourcesDir)) {
        throw new SecurityException("Path traversal detected");
    }

    Files.write(targetPath, data);
    return id;
}
```

## Authentication & Authorization

### JWT Security

**Best Practices**:
- Use strong secret (min 256 bits) from environment variable
- Token expiration: 15 minutes for access, 7 days for refresh
- Algorithm: HS256 or RS256 (never `none`)
- Include user ID, roles in claims
- Validate signature, expiration, issuer on every request
- Implement token refresh flow
- Revoke tokens on logout (store revoked JTIs in Redis/DB)

**Example**:
```java
// SECURE: Proper JWT config
public record JwtConfig(
    @ConfigProperty(name = "jwt.secret") String secret,  // From env var
    @ConfigProperty(name = "jwt.issuer") String issuer,
    @ConfigProperty(name = "jwt.expiration.access") Duration accessExpiration,  // 15m
    @ConfigProperty(name = "jwt.expiration.refresh") Duration refreshExpiration  // 7d
) {
    public JwtConfig {
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT secret too short (min 32 bytes)");
        }
    }
}
```

### Password Security

**Best Practices**:
- Use BCrypt with cost factor ≥12 (recommended: 14)
- Never log passwords or password hashes
- Implement password requirements (min 12 chars, complexity)
- Rate limit login attempts (5 attempts, 15 minute lockout)
- Log failed login attempts for monitoring
- Implement account lockout after repeated failures
- Use timing-safe comparison for password checks

**Example**:
```java
// SECURE: BCrypt with proper cost
public class PasswordService {
    private static final int BCRYPT_COST = 14;

    public String hash(String password) {
        validatePasswordStrength(password);
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
    }

    public boolean verify(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (Exception e) {
            // Don't leak timing information
            BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
            return false;
        }
    }
}
```

### Authorization Patterns

**Best Practices**:
- Use `@RolesAllowed` on all non-public endpoints
- Verify ownership for all resource operations (not just role)
- Implement resource-level permissions (can user edit this match?)
- Default to deny (whitelist, not blacklist)
- Check authorization in service layer, not just REST layer

**Example**:
```java
// SECURE: Role + ownership check
@DELETE
@Path("/{id}")
@RolesAllowed({"ADMIN", "USER"})
public Response deleteContainer(@PathParam("id") UUID id, @Context SecurityContext sec) {
    ContainerId containerId = new ContainerId(id);
    String userId = sec.getUserPrincipal().getName();

    // Admin can delete any, users only their own
    if (!sec.isUserInRole("ADMIN")) {
        Container container = containerService.findById(containerId)
            .orElseThrow(() -> new NotFoundException());

        if (!container.isOwnedBy(userId)) {
            throw new ForbiddenException("Not your container");
        }
    }

    containerService.delete(containerId);
    return Response.noContent().build();
}
```

## Input Validation

### REST API Validation

**Best Practices**:
- Use `@Valid` on all request DTOs
- Validate all path/query parameters
- Use Bean Validation annotations (`@NotNull`, `@Size`, `@Min`, etc.)
- Implement custom validators for domain constraints
- Return 400 (not 500) for validation failures
- Don't include sensitive data in error messages

**Example**:
```java
// SECURE: Comprehensive validation
public record CreateMatchRequest(
    @NotBlank(message = "Match name required")
    @Size(min = 3, max = 50, message = "Name must be 3-50 chars")
    @Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Name contains invalid chars")
    String name,

    @NotNull(message = "Modules list required")
    @Size(min = 1, max = 10, message = "Must enable 1-10 modules")
    List<@NotBlank String> enabledModules,

    @Min(value = 1, message = "Tick rate must be positive")
    @Max(value = 128, message = "Tick rate too high")
    int tickRate
) {}
```

### Query Injection Prevention

**Best Practices**:
- NEVER concatenate strings to build queries
- Use parameterized queries or query builders
- For MongoDB: use `Filters.eq()`, not string concatenation
- Validate/sanitize input even when using safe APIs
- Escape special characters in search terms

**Example**:
```java
// SECURE: Parameterized query
public Optional<User> findByUsername(String username) {
    // MongoDB - use Filters API
    Document result = collection.find(
        Filters.eq("username", username)  // Safe, not concatenated
    ).first();

    return Optional.ofNullable(result).map(UserMapper::fromDocument);
}

// INSECURE: String concatenation
public Optional<User> findByUsername(String username) {
    Document result = collection.find(
        new Document("username", username)  // Still safe
    ).first();

    // NEVER DO THIS:
    // String query = "{username: '" + username + "'}";  // INJECTION!

    return Optional.ofNullable(result).map(UserMapper::fromDocument);
}
```

## Secrets Management

**Best Practices**:
- NEVER commit secrets to git (add to .gitignore)
- Use environment variables for all secrets
- Use `@ConfigProperty` with `defaultValue = ""` (fail if missing)
- Rotate secrets regularly (JWT secret, DB password, API keys)
- Different secrets per environment (dev, staging, prod)
- Use secret management service in production (AWS Secrets Manager, Vault)

**Example**:
```java
// SECURE: Environment variable
@ConfigProperty(name = "jwt.secret")
String jwtSecret;

@ConfigProperty(name = "mongodb.password")
String dbPassword;

@ConfigProperty(name = "openai.api.key")
Optional<String> openaiKey;  // Optional for local dev
```

**`.gitignore` must include**:
```
.env
.env.*
*.key
*.pem
application-prod.properties
secrets/
```

## Dependencies

**Best Practices**:
- Run `mvn dependency:tree` regularly to audit deps
- Use Dependabot or Renovate for automated updates
- Subscribe to security mailing lists (Quarkus, Java, MongoDB)
- Update dependencies monthly (or immediately for critical CVEs)
- Use Maven Enforcer Plugin to ban vulnerable versions
- Scan with OWASP Dependency Check in CI/CD

**Example Maven Config**:
```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>8.4.0</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
    </configuration>
</plugin>
```

## Logging & Monitoring

**Security Event Logging**:
- Log all authentication attempts (success/failure)
- Log authorization failures
- Log sensitive operations (delete, role changes)
- DON'T log passwords, tokens, or sensitive data
- Include user ID, IP, timestamp, action in logs
- Ship logs to centralized system (ELK, Splunk)

**Example**:
```java
// SECURE: Proper security logging
@Override
public AuthToken authenticate(String username, String password) {
    Optional<User> user = userRepository.findByUsername(username);

    if (user.isEmpty() || !passwordService.verify(password, user.get().passwordHash())) {
        logger.warn("Failed login attempt for username: {} from IP: {}",
            username, requestContext.getRemoteAddr());

        throw new AuthenticationException("Invalid credentials");
    }

    logger.info("Successful login for user: {} (ID: {})",
        username, user.get().id());

    return jwtService.generateToken(user.get());
}

// INSECURE: Logs password
logger.debug("Authenticating user {} with password {}", username, password);  // NEVER!
```

## Testing Security

**Security Tests to Include**:
- Unit tests for authorization logic
- Integration tests with different roles
- Test invalid/malicious input handling
- Test rate limiting behavior
- Test JWT expiration and revocation
- Test CORS configuration
- Test file upload restrictions

**Example**:
```java
@Test
void deleteContainer_asNonOwner_returns403() {
    // Arrange
    Container container = containerService.create("test");
    String ownerId = "owner-123";
    String otherUserId = "other-456";

    // Act & Assert
    given()
        .auth().oauth2(generateToken(otherUserId, "USER"))
    .when()
        .delete("/api/containers/" + container.getId().value())
    .then()
        .statusCode(403);
}
```

## Checklist for New Features

Before merging any PR, verify:

- [ ] All endpoints have `@RolesAllowed` or explicit auth check
- [ ] All DTOs have validation annotations
- [ ] No secrets in code or config files
- [ ] No string concatenation in queries
- [ ] File uploads validate type, size, path
- [ ] WebSocket endpoints authenticate in `@OnOpen`
- [ ] Commands validate input and ownership
- [ ] Security events are logged
- [ ] Dependencies are up to date (no known CVEs)
- [ ] Tests cover authorization logic
- [ ] Error messages don't leak sensitive data
