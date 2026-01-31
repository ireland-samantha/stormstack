---
name: docs-rewrite
description: "Comprehensive documentation rewrite for software projects with rigorous verification. Performs deep codebase analysis, verifies all claims against actual implementation, then generates honest, accurate, developer-friendly documentation. Use when: (1) Creating docs from scratch for an undocumented project, (2) Rewriting outdated or incomplete documentation, (3) Generating README and technical docs that reflect actual implementation state, (4) Documenting multi-service architectures. Triggers: 'rewrite docs', 'update documentation', 'document this project', 'generate docs from code', 'fix outdated docs'."
---

# Documentation Rewrite Skill

Generate honest, accurate documentation by understanding the codebase deeply and verifying every claim against reality.

## Project Context (for writing accurate docs)

Stormstack is a cloud-native multiplayer game server framework. The core thesis: game servers have historically been bespoke, monolithic infrastructure—but they're fundamentally just stateful services with real-time requirements. Modern distributed systems patterns (service meshes, declarative configuration, horizontal scaling, proper observability) translate well to game workloads when adapted thoughtfully.

## Core Principle: Don't Lie to the Reader

Documentation that describes features that don't exist is worse than no documentation. Users will find out. It wastes their time and erodes trust. Every statement gets verified against actual code.

## Voice & Tone

Write documentation like you're explaining to a smart colleague who's new to the codebase:

- **Humble**: "This works, but there's room to improve X"
- **Confident**: State what works clearly, no hedging on verified facts
- **Transparent**: If something's half-baked, say so
- **Fun where appropriate**: Technical doesn't mean boring
- **Respectful of reader's time**: Get to the point

Examples of good tone:
- "The auth service handles JWT validation and session management. It's solid for single-node deployments; distributed session support is on the roadmap."
- "Fair warning: the config system is powerful but has some sharp edges. See Gotchas below."
- "This actually works pretty well! Here's how to get started..."

Avoid:
- Corporate speak ("leverage", "utilize", "facilitate")
- False confidence ("robust", "enterprise-grade" when it's not)
- Unnecessary hedging on things you've verified

## Arguments

- `--scope-file <path>` - Path to scope.json (from premerge) - limits doc updates to changed areas
- `--full` - Rewrite all documentation (default if no scope)

## Expected Documentation Structure

The docs folder should contain these files covering these topics:

### Required Documentation Files

| File | Topic | Description |
|------|-------|-------------|
| `docs/architecture.md` | System Architecture | High-level overview, service boundaries, data flow diagrams, design decisions |
| `docs/api-reference.md` | REST API Reference | All endpoints, request/response formats, auth requirements, examples |
| `docs/module-system.md` | Module Hot-Reload | Module lifecycle, installation, ClassLoader isolation, security model |
| `docs/classloaders.md` | ClassLoader Isolation | Container isolation, module sandboxing, security boundaries |
| `docs/control-plane.md` | Control Plane Service | Node registry, match routing, autoscaling, cluster management |
| `docs/game-sdk.md` | Game SDK | ECS patterns, component creation, system implementation, module development |
| `docs/testing.md` | Testing Guide | Unit tests, integration tests, Testcontainers, test patterns |
| `docs/docker.md` | Docker & Deployment | Container builds, docker-compose, production deployment |
| `docs/frontend.md` | Web Panel | React admin panel, components, state management |
| `docs/rendering-library.md` | Rendering Engine | NanoVG framework, GUI components, test framework |
| `docs/performance.md` | Performance Tuning | JVM flags, connection pools, tick optimization |
| `docs/new-service-setup.md` | Adding Services | Step-by-step guide for new service modules |
| `docs/useful-commands.md` | CLI Commands | Build commands, dev scripts, debugging |
| `docs/why.md` | Project Philosophy | Design rationale, trade-offs, goals |
| `docs/ai.md` | AI Integration | LLM patterns, AI game masters, prompt engineering |

### Topics Each Doc Must Cover

**architecture.md**:
- Service boundaries (Thunder Engine, Auth, Control Plane)
- ECS architecture overview
- Container isolation model
- WebSocket streaming architecture
- ASCII/Mermaid system diagram

**api-reference.md**:
- All REST endpoints with methods, paths, descriptions
- Request/response body schemas
- Authentication requirements per endpoint
- Error response formats
- WebSocket endpoint documentation

**module-system.md**:
- Module lifecycle (load, install, unload)
- ModuleFactory pattern
- ECS integration (components, systems)
- Hot-reload mechanism
- Security restrictions

**classloaders.md**:
- ContainerClassLoader design
- Parent delegation model
- Module isolation guarantees
- Thread safety considerations

**control-plane.md**:
- Node registration and heartbeat
- Match routing algorithm
- Autoscaler configuration
- Module distribution
- Dashboard API

**game-sdk.md**:
- Component definition patterns
- System implementation
- Command handling
- Event publishing
- Example game module

**testing.md**:
- Test structure (unit vs integration)
- Testcontainers for MongoDB
- API acceptance tests
- WebSocket testing patterns
- Test coverage expectations

**docker.md**:
- Multi-stage build process
- docker-compose.yml explanation
- Environment variables
- Production deployment checklist

### Documentation Verification Checklist

When writing/updating docs, verify:
- [ ] Every endpoint documented exists (`grep -r "@Path"`)
- [ ] Every config option traced to usage (`grep -r "@ConfigProperty"`)
- [ ] Every service documented has implementation (`find . -name "*ServiceImpl.java"`)
- [ ] Code examples compile or are syntax-verified
- [ ] File paths in docs exist (`ls -la path/from/doc`)
- [ ] Docker commands actually work
- [ ] Build commands match `build.sh`

## Autonomous Operation

When invoked, this skill should:
1. **Analyze first** - Read code before writing docs
2. **Verify everything** - Don't document features that don't exist
3. **Auto-fix when possible** - Update outdated info automatically
4. **Report issues** - Flag docs that need human review
5. **Minimize prompts** - Only ask user when truly ambiguous

**Auto-fix scenarios** (do these without asking):
- Update endpoint paths that changed
- Fix configuration option names
- Update version numbers
- Correct file paths
- Update ASCII diagrams to match current structure

**Require confirmation for**:
- Removing documentation for deleted features
- Major architectural rewrites
- Adding new documentation files

## Workflow

### Phase 0: Load Scope (if from premerge)

```bash
# Check if invoked from premerge with scope
if [[ -f .review-output/scope.json ]]; then
  SCOPE=$(jq -r '.scope' .review-output/scope.json)
  FILES=$(jq -r '.files[]' .review-output/scope.json)

  echo "Scoped documentation review: $SCOPE"

  # Determine which docs need updating based on changed files
  # - If core modules changed → update architecture docs
  # - If API endpoints changed → update API docs
  # - If config changed → update configuration docs
  # - If new features added → update feature docs

  # Focus deep-dive on changed areas
  echo "$FILES" > /tmp/changed-files.txt
  FOCUSED=true
else
  # Full documentation rewrite
  echo "Full documentation rewrite"
  FOCUSED=false
fi
```

**Scope-aware behavior:**
- **commit/branch scope**: Only update docs related to changed components
- **working-tree scope**: Update docs for modified areas
- **full scope**: Complete documentation rewrite

### Phase 1: Codebase Deep-Dive

Before writing anything, understand what actually exists.

**If FOCUSED=true**, limit deep-dive to areas affected by changed files.
**If FOCUSED=false**, analyze entire codebase.

**1. Map the territory**
```bash
tree -L 3 -I 'node_modules|target|build|dist|.git|__pycache__'

# Find all modules/services
find . -name "pom.xml" -o -name "build.gradle" -o -name "package.json" -o -name "go.mod" -o -name "Cargo.toml" | head -20

# Find entry points
find . -type f -name "*.java" | xargs grep -l "public static void main" 2>/dev/null
```

**2. For each component, gather evidence**

Answer these with grep results and code reads, not assumptions:

- What does it actually do? (read main class/entry point)
- What APIs does it expose? (find endpoints, handlers)
- What config does it read? (search for env vars, config loading)
- What does it talk to? (find client instantiations, connection strings)

**3. Trace real data flow**
```bash
# Find API endpoints
grep -rn "@GetMapping\|@PostMapping\|router\." --include="*.java" --include="*.go"

# Find config loading
grep -rn "getenv\|@Value\|config\." --include="*.java" --include="*.go"
```

### Phase 2: Verify Everything

**This is the critical part. No shortcuts.**

**1. Verify features exist and work**
```bash
# About to document "hot-reload support"? Prove it:
grep -rn "reload\|hotswap\|ClassLoader" --include="*.java"
# Then READ the implementation. Is it real or a stub?
```

**2. Verify APIs are implemented**
```bash
# Find the endpoint
grep -rn "/api/users" --include="*.java"
# Read the handler - does it do things or return TODO?
```

**3. Verify config is actually used**
```bash
# Trace from definition to usage
grep -rn "MAX_CONNECTIONS" --include="*.java" --include="*.yaml"
```

**4. Run the tests**
```bash
./mvnw test -q 2>&1 | tail -30
# or: npm test, go test ./..., cargo test

# Test results reveal truth: what passes vs what's aspirational
```

**5. Actually try to build and run it**
```bash
./mvnw package -DskipTests -q 2>&1 | tail -10

# Check if help text matches reality
java -jar target/*.jar --help 2>/dev/null
```

### Phase 3: Classify What You Found

Be honest about implementation state:

| State | How to Document |
|-------|-----------------|
| **Works** | Document fully, with examples |
| **Mostly works** | Document what works, note the gaps clearly |
| **Stubbed/WIP** | Roadmap section only, or omit entirely |
| **Broken** | Don't document as working; mention in known issues if relevant |
| **Planned** | Roadmap only, clearly marked "not yet implemented" |

### Phase 4: Write the Docs

**Per-component structure:**

```markdown
# Component Name

What it actually does in 1-2 sentences. No fluff.

## Status

Be real: production-ready / beta / experimental / under active development.

## Quick Start

Steps that actually work. You verified these by running them.

## Configuration

Options that are actually read and used. Defaults verified in code.
Note any gotchas or non-obvious behavior.

## API Reference

Endpoints that exist and return real responses.
Skip anything that returns 501 or TODO.

## How It Works

The real architecture. How data actually flows.
Only include diagrams that reflect current reality.

## Limitations & Known Issues

Be upfront. List what doesn't work or has sharp edges.
Users will discover these anyway—better they hear it from you.

## Roadmap (if applicable)

Clearly separated from "what exists."
"Planned" means not implemented yet.
```

**README.md specifically:**

- Briefly describe the vision, then describe what the project IS today
- Quick start that actually works (you ran it)
- Honest status section
- Link to detailed docs

Generate a diagram of the whole system architecture, and add it to the architecture docs page, ascii okay.
### Phase 5: Final Verification

Before delivering:

```bash
# Every class/method you referenced exists
grep -rn "TheClassYouMentioned" --include="*.java"

# Every file path you mentioned exists
ls -la the/path/you/mentioned

# Every config key you documented is actually used
grep -rn "config.key.you.documented" --include="*.java" --include="*.yaml"
```

## Anti-Patterns

- **Aspirations as features**: "Supports clustering" when there's a TODO in the clustering code
- **Design doc copy-paste**: Design describes intent. Code describes reality. They drift.
- **Documenting stubs**: `throw new NotImplementedException()` is not a feature
- **Hidden requirements**: "Run the server" (requires Docker, Java 21, 4 env vars, and a prayer)

## Phrasing Guide

When something's not fully there, be direct but not apologetic:

- "Works for X. Y support is coming."
- "Handles most cases well. Edge case Z needs manual intervention for now."
- "Experimental—API may change."
- "Solid for development. Production deployment docs are in progress."

When something works well, say so confidently:

- "This works. Here's how."
- "Battle-tested in [context]."
- "The happy path is smooth. See Gotchas for edge cases."

## Collaboration with Other Skills

This skill actively collaborates with the review system:

### Receiving Collaboration Requests

When other skills flag documentation needs:
```json
{
  "collaborationNeeded": [
    {
      "skill": "write-docs",
      "reason": "New endpoint POST /api/matches needs documentation",
      "files": ["MatchResource.java"],
      "suggestedContent": {
        "section": "API Reference",
        "topics": ["Match creation", "Request format", "Response codes"]
      }
    }
  ]
}
```

**Response Actions:**
| Request Source | Documentation Action |
|----------------|---------------------|
| self-review: new endpoint | Add to api-reference.md |
| self-review: new config | Add to relevant service doc |
| security-review: auth changes | Update security sections |
| solid-review: architecture change | Update architecture.md |

### Sending Collaboration Requests

When documentation reveals code issues:
```json
{
  "collaborationRequests": [
    {
      "targetSkill": "self-review",
      "reason": "Documented endpoint /api/old doesn't exist in code",
      "action": "VERIFY_OR_REMOVE"
    },
    {
      "targetSkill": "test-coverage",
      "reason": "New feature documented but no tests found",
      "files": ["NewFeatureService.java"]
    }
  ]
}
```

### Documentation Auto-Updates

When invoked via collaboration, this skill:
1. Reads the collaboration request context
2. Analyzes the specified files
3. Generates appropriate documentation
4. Writes updates to the correct doc files
5. Reports what was added/changed

**No user prompts for:**
- Adding new endpoint to existing API docs
- Updating configuration option descriptions
- Fixing file paths or class names
- Adding missing sections to existing docs

**Prompt user for:**
- Creating entirely new documentation files
- Major restructuring of documentation
- Removing documentation for deleted features

## Final Checklist

- [ ] Every feature documented exists in code (grep verified)
- [ ] Every config option traced from definition to usage
- [ ] Quick start steps actually ran successfully
- [ ] Code examples tested or syntax-verified
- [ ] No stubs documented as features
- [ ] Status/maturity stated honestly
- [ ] Limitations section exists and is truthful
- [ ] All file paths verified to exist
- [ ] Tone is helpful, not defensive or salesy
- [ ] Collaboration requests from other skills addressed