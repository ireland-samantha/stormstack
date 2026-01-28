# Self-Review Skill

Perform a comprehensive self-review of the current commit against CLAUDE.md guidelines.

## Instructions

1. **Read CLAUDE.md** to understand all project guidelines

2. **Get commit details** using `git show --stat HEAD`

3. **Review changed files** against each section of CLAUDE.md:

### Core Principles
- **Separation of Concerns**: Each module has one clear responsibility
- **Single Responsibility Principle**: A module should have only one reason to change
- **Dependency Injection**: All dependencies injected, not instantiated internally
- **Depend on Abstractions**: Depend on interfaces, not implementations
- **Clean Architecture Layers**: Core (no framework deps) → Implementation → Adapters → Providers

### Architecture Patterns
- **Fluent API Pattern**: Use `container.lifecycle().start()` style, not direct methods
- **Module System**: Modules implement `ModuleFactory` interface
- **ECS Pattern**: Array-based columnar storage for O(1) component access

### Java Conventions
- Java 25 with preview features (virtual threads, pattern matching, records)
- Prefer immutability (`final` fields, unmodifiable collections)
- Use `Optional` for nullable returns, never for parameters
- Use records for DTOs with validation annotations

### Quarkus Specifics
- Interfaces in `engine-core` with no framework annotations
- Quarkus annotations only on implementation classes
- Repository pattern with MongoDB

### Naming Conventions
- Domain model: `Match`, `ExecutionContainer`
- Strongly-typed ID: `MatchId`, `ContainerId`
- Service interface: `MatchService` / impl: `MatchServiceImpl`
- Request DTO: `CreateMatchRequest` / Response: `MatchResponse`

### Quality Gates
- [ ] Interface has complete Javadoc
- [ ] DTOs use Java records with validation annotations
- [ ] Custom exceptions for all failure cases
- [ ] Unit tests for all classes (>80% coverage)
- [ ] `./build.sh all` passes

### Code Quality Philosophy
- **No deprecation**: Never use `@Deprecated`, apply full migration
- **Complete refactoring**: Update all affected code across layers
- **YAGNI**: Don't build features you don't need yet

4. **Evaluate test coverage** by checking which new production files have corresponding test files

5. **Create self-review.json** in the project root:
```json
{
  "review": { "commit": "...", "date": "..." },
  "findings": [
    {
      "severity": "low|medium|high",
      "section": "Core Principles|Architecture|Java Conventions|Quarkus|Quality Gates|Code Quality",
      "file": "...",
      "line": N,
      "description": "...",
      "recommendation": "..."
    }
  ],
  "testCoverage": {
    "summary": "...",
    "estimatedCoverage": "..."
  },
  "grade": {
    "overall": "A-F",
    "breakdown": {
      "codeQuality": "...",
      "testCoverage": "...",
      "guidelineAdherence": "..."
    }
  },
  "recommendations": [
    {
      "priority": "high|medium|low",
      "action": "...",
      "reason": "..."
    }
  ]
}
```

6. **Address high-priority feedback** immediately

7. **Report summary** to user including:
   - Overall grade
   - Number of findings by severity
   - High-priority items fixed
   - Remaining items for consideration

## Key Checks

### Must Pass
- No `@Deprecated` annotations in new code
- Interfaces in `engine-core` have no Quarkus annotations
- All DTOs are Java records
- `./build.sh all` passes

### Should Check
- Fluent API patterns used for container operations
- Strongly-typed IDs for all entities
- Custom exceptions extend `EngineException`
- Repository interfaces in `engine-core`, implementations in `persistence/`

### Code Smells
- Framework annotations on interfaces
- `new` keyword for dependencies (should be injected)
- Nullable parameters (should use `Optional` or overloads)
- Missing Javadoc on public interfaces
