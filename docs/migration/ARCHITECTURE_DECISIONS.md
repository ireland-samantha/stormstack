# Architecture Decisions

This document records all significant architectural decisions made during the Java to Rust migration.

---

## Decision: Use wasmtime for WASM sandbox
**Date:** 2026-02-02
**Status:** ACCEPTED (MANDATORY - do not re-evaluate)
**Rationale:**
- Best security audit history among WASM runtimes
- Epoch interruption support for reliable timeout handling
- Fuel metering for fine-grained resource control
- Bytecode Alliance backing ensures long-term maintenance
- Strong integration with Rust ecosystem

**Alternatives Considered:**
- wasmer: Faster cold start but less security focus
- wasmtime is mandated by project requirements

**Implications:**
- Must use wasmtime-wasi for WASI support
- Epoch interruption requires background thread to increment epochs
- Fuel metering must be enabled at engine creation time

---

## Decision: Use legion for ECS
**Date:** 2026-02-02
**Status:** ACCEPTED (MANDATORY - do not re-evaluate)
**Rationale:**
- High-performance archetype-based ECS
- Excellent Rust integration
- Good documentation and community support
- Meets performance target of ≥746 ticks/sec with 10k entities

**Alternatives Considered:**
- bevy_ecs: Tightly coupled to Bevy game engine
- specs: Older, less performant
- hecs: Simpler but fewer features
- legion is mandated by project requirements

**Implications:**
- Must use legion's World, Entity, Component patterns
- Schedule system for parallel system execution
- Query system for component access

---

## Decision: Use axum for HTTP/WebSocket
**Date:** 2026-02-02
**Status:** ACCEPTED
**Rationale:**
- Modern async Rust web framework
- Built-in WebSocket support via axum::extract::ws
- Tower middleware compatibility
- Strong type safety
- Excellent performance

**Alternatives Considered:**
- actix-web: Good performance but more complex
- warp: Filter-based API less intuitive
- rocket: Less async-first

**Implications:**
- WebSocket handlers via axum::extract::WebSocketUpgrade
- Tower middleware for authentication, logging
- Shared state via axum::Extension or State

---

## Decision: Use jsonwebtoken for JWT
**Date:** 2026-02-02
**Status:** ACCEPTED
**Rationale:**
- De facto standard JWT library for Rust
- Supports all common algorithms
- Good validation options
- Active maintenance

**Implications:**
- Must define Claims struct matching Java JWT structure
- Must maintain algorithm compatibility with existing tokens

---

## Decision: Use argon2 for password hashing
**Date:** 2026-02-02
**Status:** ACCEPTED
**Rationale:**
- Current best practice for password hashing
- Memory-hard to resist GPU attacks
- Winner of Password Hashing Competition

**Implications:**
- Existing BCrypt hashes must be migrated or dual-supported
- May need migration strategy for existing passwords

---

## Decision: Use tokio as async runtime
**Date:** 2026-02-02
**Status:** ACCEPTED
**Rationale:**
- Industry standard async runtime for Rust
- Excellent ecosystem support
- Work-stealing scheduler for efficiency
- Required by axum, reqwest, and other dependencies

**Implications:**
- All async code uses tokio primitives
- Must use #[tokio::main] for entry point
- Must use #[tokio::test] for async tests

---

## Decision: Workspace structure with feature-based crates
**Date:** 2026-02-02
**Status:** ACCEPTED
**Rationale:**
- Clear separation of concerns
- Independent compilation and testing
- Matches Java module structure conceptually
- Enables parallel development by different agents

**Crate Structure:**
```
stormstack/
├── Cargo.toml (workspace)
├── stormstack-core/          # Shared types, traits, errors
├── stormstack-ecs/           # ECS implementation (legion wrapper)
├── stormstack-wasm/          # WASM sandbox (wasmtime)
├── stormstack-wasm-host/     # WASM host functions
├── stormstack-auth/          # Authentication & authorization
├── stormstack-net/           # Networking primitives
├── stormstack-ws/            # WebSocket handling
├── stormstack-modules/       # Native hot-reload modules
├── stormstack-server/        # Main server binary
└── stormstack-test-utils/    # Test utilities
```

**Implications:**
- Each crate has clear ownership boundary
- Interface-first development required
- Must maintain dependency graph (no cycles)

---

## Decision: Two-tier module system (Native + WASM)
**Date:** 2026-02-02
**Status:** ACCEPTED
**Rationale:**
- Native hot-reload for trusted internal plugins (developer use)
- WASM sandbox for untrusted user-uploaded code
- Clear security boundary between trust levels

**Native Hot Reload (libloading):**
- For internal/developer use only
- Loads trusted Rust code compiled to .so/.dylib
- Fast iteration during development
- NOT exposed to end users

**WASM Modules (wasmtime):**
- For user-uploaded code
- Fully sandboxed, untrusted
- Resource-limited (fuel, memory, time)
- What tenants upload

**Implications:**
- Module System Agent handles native hot reload
- WASM Agent handles WASM modules
- Clear interface between them via HostFunctionProvider trait

---

## Decision: Capability-based security for WASM
**Date:** 2026-02-02
**Status:** ACCEPTED
**Rationale:**
- WASM modules have ZERO capabilities by default
- Explicit capability grants via host functions
- Follows principle of least privilege
- Matches wasmtime's security model

**Default Denials:**
- File system access: DENIED
- Network access: DENIED
- Environment variables: DENIED
- Process spawning: DENIED
- Time access: Limited (can be denied)

**Implications:**
- All capabilities explicitly granted via Linker
- Host functions must validate all inputs
- Security review required for any new host function

---

<!-- Add new decisions above this line -->
