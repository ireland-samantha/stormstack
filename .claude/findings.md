# Adversarial Review - Classified Findings

**Generated:** 2026-02-03 15:02
**Updated:** 2026-02-03 15:11 (RECONVENE COMPLETE)
**Total Issues:** 28
**Status:** TEAM AGREEMENT REACHED - READY FOR FIX

## Classification Key

- **DOC FIX**: Docs are wrong, code is correct - fix the documentation
- **CODE FIX**: Feature should exist but doesn't - add to codebase
- **BOTH**: Partial implementation needs code work + docs need clarification

## Final Classification Summary

| Classification | Count | Description |
|----------------|-------|-------------|
| DOC FIX | 23 | WebSocket paths, CLI syntax, prerequisites, proto schemas, naming |
| CODE FIX | 1 | `lightning auth refresh` command |
| BOTH | 1 | thunder vs lightning binary naming |
| VERIFIED OK | 8 | Auth/architecture documentation accurate |

## Decisions Made

1. **Exponential backoff**: DOC FIX - remove claim (sliding window is sufficient)
2. **CLI flag-based vs context-based**: DOC FIX - context-based design is better
3. **WebSocket paths**: DOC FIX - code paths are correct design

---

## DOC FIX Issues (23)

### WebSocket Path Issues (6) - Assigned: Skeptic-1

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S1 | `api/websocket-api.rst:33` | Path `/ws/containers/{id}/snapshots/{matchId}` wrong | Change to `/ws/containers/{id}/matches/{matchId}/snapshot` |
| S1 | `api/websocket-api.rst:75-77` | Delta path wrong | Change to `/ws/containers/{id}/matches/{matchId}/delta` |
| S1 | `api/websocket-api.rst:159-161` | Commands path missing `/ws/` prefix | Add prefix, verify full path |
| S3 | `thunder/engine/websocket.rst:32-42` | 5 of 6 endpoints have wrong paths | Rewrite entire endpoint section |
| S2 | `tutorials/hello-stormstack.rst:204` | Path ends in `snapshots` (plural) | Change to `snapshot` (singular) |
| S1 | `api/index.rst:134` | Commands WS path inconsistent | Align with correct path structure |

### Proto Schema Issues (2) - Assigned: Skeptic-1

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S1 | `api/websocket-api.rst:172-188` | `CommandBatch` proto schema doesn't match code | Update to match actual `CommandRequest` structure |
| S3 | `thunder/engine/websocket.rst:349-366` | `DeltaSnapshot` proto doesn't exist | Remove or mark as "planned" |

### CLI Command Syntax Issues (6) - Assigned: Skeptic-2

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S2 | `tutorials/hello-stormstack.rst:60` | `lightning node context node` doesn't exist | Change to `lightning node context set` |
| S2 | `tutorials/hello-stormstack.rst:90` | `lightning node simulation tick` wrong | Change to `lightning node tick advance` |
| S2 | `tutorials/hello-stormstack.rst:209` | `lightning ws connect --snapshot` flag syntax wrong | Change to `lightning ws connect snapshot` |
| S2 | `lightning/cli/commands.rst:365` | `--match` and `--params` flags don't exist | Rewrite to use context pattern |
| S2 | `lightning/cli/commands.rst:389` | `--match` flag doesn't exist for snapshot | Rewrite to use context pattern |
| S2 | `lightning/cli/index.rst:23-26` | Confusion between `thunder` and `lightning` binaries | Clarify naming, remove stale binary reference |

### Missing Prerequisites Issues (4) - Assigned: Skeptic-2

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S2 | `tutorials/hello-stormstack.rst:172` | Missing context prerequisite for `simulation play` | Add prerequisite steps |
| S2 | `tutorials/first-module.rst:232` | Missing auth prerequisite for `module upload` | Add `lightning auth login` step |
| S2 | `tutorials/first-module.rst:249` | EntityModule origin unexplained | Document built-in modules |
| S2 | `tutorials/first-module.rst:266` | `spawn` command origin unexplained | Clarify command source |

### Miscellaneous Doc Issues (1) - Assigned: Skeptic-1

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S1 | `getting-started/quickstart.rst:95-96` | Match ID format unexplained | Add format explanation: `nodeId-containerId-matchId` |

---

### Exponential Backoff Claim (1) - Assigned: Skeptic-3

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S3 | `thunder/auth/authentication.rst:278-293` | Exponential backoff not implemented | **DOC FIX**: Remove claim. Sliding window rate limiting is sufficient. |

### Installation Doc Issues (3) - Assigned: Skeptic-1

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S1 | `getting-started/installation.rst:45` | Repo URL uses `ireland-samantha/lightning-engine` | Verify correct URL and update docs |
| S1 | `getting-started/installation.rst:70-74` | MongoDB 6.0+ vs docker-compose mongo:7 | Align version claims |
| S1 | `getting-started/installation.rst` | Image naming `samanthacireland/thunder-*` vs `lightning-*` | Clarify naming scheme |

### Security Documentation Gap (1) - Assigned: Skeptic-2

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S2 | `lightning/cli/configuration.rst:17` | JWT in config file without permission warning | Add security note about `chmod 600 ~/.lightning.yaml` |

---

## CODE FIX Issues (1)

### Missing CLI Command (1) - Assigned: Skeptic-2

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S2 | `lightning/cli/configuration.rst:203` | `lightning auth refresh` command doesn't exist | Implement the command in Go CLI (backend `RefreshTokenGrantHandler.java` ready) |

---

## BOTH Issues (1)

### Binary Naming Confusion (1) - Assigned: Skeptic-2

| Source | Location | Issue | Fix Required |
|--------|----------|-------|--------------|
| S2 | `lightning/cli/index.rst:23-26` | Pre-built `thunder` binary vs documented `lightning` | Standardize on `lightning`, remove/rename `thunder` binary, update docs |

---

## VERIFIED OK (Not Issues) - 8 items

From Skeptic-3 verification:
- OAuth2 grant types (all 4 implemented)
- @Scopes authorization annotation
- Role inheritance with transitive resolution
- ArrayEntityComponentStore with Float.NaN sentinel
- DeltaCompressionService with dirty tracking
- Redis state persistence
- Least-loaded scheduler
- Concepts documentation (modest, accurate claims)

---

## Proposed Assignments

### Skeptic-1 (API/WebSocket Expertise)
- All WebSocket path fixes in `api/websocket-api.rst`
- All WebSocket path fixes in `api/index.rst`
- Proto schema documentation in `api/websocket-api.rst`
- Match ID format documentation in `getting-started/quickstart.rst`
- Installation doc inconsistencies in `getting-started/installation.rst`

**Files:** 4 | **Issues:** 11

### Skeptic-2 (CLI/Tutorial Expertise)
- CLI command syntax fixes in `tutorials/hello-stormstack.rst`
- CLI command syntax fixes in `lightning/cli/commands.rst`
- Binary naming clarity in `lightning/cli/index.rst`
- Missing prerequisites in tutorials
- Implement `lightning auth refresh` command (CODE FIX)
- Security warning for config file (BOTH)

**Files:** 5 | **Issues:** 12

### Skeptic-3 (Auth/Architecture Expertise)
- WebSocket paths in `thunder/engine/websocket.rst`
- Proto schema claims in `thunder/engine/websocket.rst`
- Exponential backoff: implement OR remove claim (decision needed)

**Files:** 2 | **Issues:** 3

---

## Priority Order

1. **CRITICAL**: WebSocket endpoint paths (users get 404s)
2. **HIGH**: CLI command syntax (tutorials don't work)
3. **MEDIUM**: Missing prerequisites (users get confused)
4. **LOW**: Naming inconsistencies (cosmetic)

---

## RECONVENE COMPLETE - Final Assignments

### Skeptic-1 (API/WebSocket Expertise)
- `docs/source/api/websocket-api.rst` - WebSocket paths + proto schema
- `docs/source/api/rest-api.rst` - Verify REST endpoint paths
- `docs/source/api/index.rst` - Align WebSocket path documentation
- `docs/source/getting-started/installation.rst` - Repo URL, MongoDB version, image naming
- `docs/source/getting-started/quickstart.rst` - Match ID format explanation

**Files:** 5 | **Issues:** 11 DOC FIX

### Skeptic-2 (CLI/Tutorial Expertise)
- `docs/source/tutorials/hello-stormstack.rst` - CLI command syntax, WebSocket path
- `docs/source/tutorials/first-module.rst` - Prerequisites, module documentation
- `docs/source/lightning/cli/commands.rst` - Remove non-existent flags
- `docs/source/lightning/cli/index.rst` - Binary naming clarity
- `docs/source/lightning/cli/configuration.rst` - Security warning
- `lightning/cli/` Go code - Implement `auth refresh` command

**Files:** 6 | **Issues:** 10 DOC FIX + 1 CODE FIX + 1 BOTH

### Skeptic-3 (Auth/Architecture Expertise)
- `docs/source/thunder/engine/websocket.rst` - All 6 WebSocket endpoints, remove proto claims
- `docs/source/thunder/auth/authentication.rst` - Remove exponential backoff claim

**Files:** 2 | **Issues:** 3 DOC FIX

---

## Decision Made

**Exponential backoff** - RESOLVED as **DOC FIX**

Team consensus: Remove the "exponential backoff" claim from authentication docs. The sliding window rate limiting is functional and sufficient. Exponential backoff can be added as a future enhancement. No false promises.
