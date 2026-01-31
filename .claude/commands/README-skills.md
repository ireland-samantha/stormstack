# Lightning Engine Review Skills

Comprehensive quality assurance workflow for pre-merge reviews.

## Overview

The Lightning Engine review system provides automated quality checks across multiple dimensions:

- **Code Quality** (SOLID principles, architecture)
- **Security** (OWASP Top 10, vulnerabilities, secrets)
- **Testing** (coverage, quality)
- **Documentation** (accuracy, completeness)
- **Commit Hygiene** (message format, build status)

## Skills

### 1. `/premerge` - Orchestrator Skill ⭐

**Triggers**: `premerge`, `pre-merge`, `full review`, `review everything`, `pre-push`, `check all`

**What it does:**
1. **Interactive scope selection** - Choose what to review
2. Runs all review skills in parallel with selected scope
3. Merges findings and deduplicates
4. Applies auto-fixes (with confirmation)
5. Generates unified summary report
6. Provides deployment verdict

**Usage:**
```bash
/premerge                    # Interactive scope selection
/premerge --scope full       # Review entire repository
/premerge --commits 3        # Review last 3 commits
/premerge --since main       # Review all changes since main
/premerge --scope working-tree  # Review only modified files
```

**Interactive Scope Options:**
1. **Last commit** - Review HEAD only (quick)
2. **Last N commits** - Review recent commits
3. **All commits since main** - Review entire feature branch
4. **Changed files in working tree** - Review unstaged/staged changes
5. **Entire repository** - Full codebase scan (slowest, most thorough)

### 2. `/self-review` - Commit Hygiene

**Triggers**: `self-review`, `review my commit`, `check my changes`

**Checks:**
- Commit message format (conventional commits)
- Build passes
- Architecture boundaries respected
- Anti-patterns (deprecated code, direct instantiation)
- Test coverage for changed files
- Documentation needs

**Scope-aware**: ✓

### 3. `/solid-review` - Architecture Quality

**Triggers**: `solid-review`, `SOLID review`, `architecture review`, `check design`

**Checks:**
- **SRP**: Lines per class, method count, dependency injection count
- **OCP**: instanceof checks, switch statements, else-if chains
- **LSP**: Override violations, null returns
- **ISP**: Fat interfaces, empty implementations
- **DIP**: Direct instantiation, concrete types, framework in core

**Scope-aware**: ✓

### 4. `/test-coverage` - Testing Quality

**Triggers**: `test coverage`, `missing tests`, `what needs tests`

**Checks:**
- Maps production files to expected test files
- Prioritizes missing tests (CRITICAL, HIGH, MEDIUM, LOW)
- Checks test quality (assertions, naming, length)
- Generates coverage percentage

**Scope-aware**: ✓

### 5. `/security-review` - Security Audit ⚠️

**Triggers**: `security-review`, `security audit`, `check security`, `find vulnerabilities`

**Checks:**
- **OWASP Top 10** (injection, broken auth, crypto failures, etc.)
- **Secrets detection** (hardcoded passwords, API keys, JWT secrets)
- **Dependency vulnerabilities** (CVE scanning)
- **Lightning Engine-specific** (module isolation, command queue, WebSocket auth)
- **Best practices** (password hashing, JWT config, input validation)

**Scope-aware**: ✓

### 6. `/write-docs` - Documentation

**Triggers**: `rewrite docs`, `update documentation`, `document this project`

**Features:**
- Deep codebase analysis
- Verifies all claims against implementation
- Honest, accurate, developer-friendly docs
- Updates only affected docs when scoped

**Scope-aware**: ✓

## Scope Flow

```
┌──────────────────┐
│  User invokes    │
│  /premerge       │
└────────┬─────────┘
         │
         ▼
┌──────────────────────┐
│  Interactive Prompt  │
│  5 scope options     │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│  Write scope.json    │
│  {                   │
│    scope: "branch"   │
│    files: [...]      │
│  }                   │
└────────┬─────────────┘
         │
         ▼
┌───────────────────────────────┐
│  All skills read scope.json   │
│  and analyze only those files │
└────────┬──────────────────────┘
         │
         ▼
┌──────────────────────┐
│  Unified report      │
│  with verdict        │
└──────────────────────┘
```

## Output Files

All generated in `.review-output/`:

```
.review-output/
├── scope.json              # Selected scope (commits/files)
├── self-review.json        # Commit hygiene findings
├── solid-review.json       # Architecture violations
├── test-coverage.json      # Missing tests
├── security-review.json    # Security vulnerabilities
├── docs-review.json        # Documentation issues
└── summary.json            # Unified report
```

## Summary Report Format

```json
{
  "meta": {
    "commit": "abc1234",
    "branch": "feature/auth",
    "scope": "branch",
    "skillsRun": ["self-review", "solid-review", "test-coverage", "security-review", "write-docs"]
  },
  "findings": {
    "total": 15,
    "critical": 0,
    "high": 3,
    "medium": 8,
    "low": 4
  },
  "grades": {
    "overall": "B",
    "build": "A",
    "security": "C",
    "architecture": "A",
    "solid": "C",
    "testing": "B",
    "documentation": "B"
  },
  "verdict": {
    "canPush": false,
    "blockers": ["2 high security issues"],
    "warnings": ["SOLID violations in UserService"]
  }
}
```

## Grading Weights

- **Security**: 25% (highest priority)
- **Testing**: 20%
- **Build**: 15%
- **Architecture**: 15%
- **SOLID**: 15%
- **Documentation**: 10%

## Can Push Criteria

✓ Build passes
✓ No critical security findings
✓ No critical/high security vulnerabilities
✓ No hardcoded secrets in production code
✓ No high architecture violations
✓ All critical services have tests
✓ Documentation accuracy ≥80%

## Individual Skill Usage

You can also run skills individually:

```bash
# Review specific commit
/self-review

# Check SOLID violations in src/main/java
/solid-review src/main/java

# Check test coverage for changed files
/test-coverage --changed

# Security audit with CVE fetch
/security-review --fetch-reports --deep

# Update docs for changed areas
/write-docs
```

## Tips

1. **Use `/premerge` before every merge** - Catches issues early
2. **Scope to "branch" for feature branches** - Faster than full scan
3. **Scope to "full" for initial setup** - Understand baseline
4. **Review security findings carefully** - Critical/High are blockers
5. **Apply auto-fixes when offered** - Saves manual work
6. **Update docs as you go** - Don't let them drift

## Reference Documentation

- **Security Best Practices**: `.claude/commands/references/security-best-practices.md`
  - Lightning Engine-specific security patterns
  - Code examples (secure vs insecure)
  - OWASP mapping
  - Pre-merge security checklist
