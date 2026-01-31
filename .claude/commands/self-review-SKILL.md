---
name: self-review
description: "Review git commits against project guidelines. Checks build, architecture, patterns, tests, docs. Triggers: 'review my commit', 'self-review', 'check my changes', 'pre-push review'."
---

# Self-Review Skill

Review commits against CLAUDE.md guidelines. Fast feedback before push.

## Autonomous Operation Mode

This skill operates **autonomously by default**. It will:
1. Detect and fix issues automatically where safe to do so
2. Only prompt the user for decisions that truly require human judgment
3. Apply fixes in batches rather than one at a time
4. Continue working through the review even if minor issues are found

**Philosophy**: A good review assistant doesn't ask permission for every small fix. It fixes what's obviously fixable and reports back what it did.

## Quick Flow

1. **Load scope** (from `.review-output/scope.json` if exists, else default to HEAD)
2. Load CLAUDE.md for project-specific rules
3. Gather commit/file info based on scope
4. Run build (if fails → **attempt auto-fix**, then re-run)
5. Check architecture boundaries → **auto-fix where possible**
6. Detect anti-patterns → **auto-fix safe patterns**
7. Verify test coverage (delegate to test-coverage)
8. Check documentation needs → **flag for write-docs collaboration**
9. Generate `self-review.json` with fixes applied
10. Present summary with changes made and remaining issues

## Arguments

- `--scope-file <path>` - Path to scope.json (from premerge)
- `--commits <N>` - Review last N commits
- `--since <ref>` - Review changes since ref

## Step 0: Load Scope

```bash
# Check if invoked from premerge (scope file exists)
if [[ -f .review-output/scope.json ]]; then
  SCOPE=$(jq -r '.scope' .review-output/scope.json)
  TARGET=$(jq -r '.target' .review-output/scope.json)
  FILES=$(jq -r '.files[]' .review-output/scope.json)
  echo "Using scope: $SCOPE ($TARGET)"
else
  # Default: review HEAD only
  SCOPE="commit"
  TARGET="HEAD"
  echo "No scope file, defaulting to HEAD"
fi
```

## Step 1: Gather Context

Based on scope, gather relevant files:

```bash
case "$SCOPE" in
  "commit")
    # Single commit
    git log -1 --format="%H %s" $TARGET
    git diff-tree --no-commit-id --name-only -r $TARGET > /tmp/changed-files.txt
    ;;

  "commits"|"branch")
    # Multiple commits
    git log --oneline $TARGET
    git diff --name-only $TARGET > /tmp/changed-files.txt
    ;;

  "working-tree")
    # Modified + untracked files
    git status --short > /tmp/git-status.txt
    (git diff --name-only && git ls-files --others --exclude-standard) > /tmp/changed-files.txt
    ;;

  "full")
    # Entire repository
    find . -type f -name "*.java" | grep -v target > /tmp/changed-files.txt
    ;;
esac

# Split into production and test files
cat /tmp/changed-files.txt | grep "\.java$" | grep -v Test > /tmp/changed-prod.txt
cat /tmp/changed-files.txt | grep "Test\.java$" > /tmp/changed-tests.txt
```

## Step 2: Commit Message

Format: `type(scope): subject`

| Check | Rule |
|-------|------|
| Type | `feat\|fix\|docs\|style\|refactor\|test\|chore` |
| Scope | Module name present |
| Subject | Imperative, lowercase, no period, ≤72 chars |

## Step 3: Build

```bash
./build.sh all 2>&1 | tee /tmp/build.log
```

**If build fails**:
1. **Analyze the error** - Parse compiler output to identify the issue
2. **Auto-fix if possible**:
   - Missing imports → Add them automatically
   - Syntax errors in modified files → Fix if straightforward
   - Type mismatches from refactoring → Propagate type changes
   - Missing method implementations → Generate stubs if interface-driven
3. **Re-run build** after fixes
4. **Only stop and report** if fixes can't be automated

**Auto-fixable build errors:**
| Error Type | Auto-Fix Action |
|------------|-----------------|
| Cannot find symbol (import) | Add missing import |
| Method does not override | Add/fix `@Override` annotation |
| Incompatible types (primitive) | Add cast if safe |
| Missing return statement | Add `return null/0/false` with TODO comment |
| Unreported exception | Add try-catch or throws declaration |

**Require manual intervention:**
| Error Type | Action |
|------------|--------|
| Architectural issues | Flag for human review |
| Complex type errors | Report with context |
| Test failures | Investigate root cause |

## Step 4: Architecture Boundaries

Check imports in changed files against CLAUDE.md layer rules.

Default rules (override via CLAUDE.md):
- `engine-core/` must not import framework classes (`io.quarkus.*`, `jakarta.*` except validation)
- `engine-internal/` must not import `webservice.*`

```bash
for f in $(grep "engine-core" /tmp/changed-prod.txt); do
  grep -Hn "import io\.quarkus\|import jakarta\." "$f" | grep -v jakarta.validation
done
```

**Severity**: HIGH

## Step 5: Anti-Patterns

| Pattern | Detection | Severity |
|---------|-----------|----------|
| `@Deprecated` in new code | `grep "@Deprecated"` | HIGH |
| Direct `new Service()` | `grep "new \w+Service("` | HIGH |
| Framework in core | `@Inject` in engine-core | HIGH |
| Missing `final` on `@Inject` | `grep "@Inject" \| grep -v final` | MEDIUM |
| DTO as class not record | `class.*Request\|Response` | MEDIUM |

## Step 6: Test Coverage

For each changed production file, verify test exists:

```bash
for f in $(cat /tmp/changed-prod.txt); do
  test_path=$(echo "$f" | sed 's|main|test|; s|\.java$|Test.java|')
  [[ -f "$test_path" ]] || echo "MISSING: $test_path"
done
```

**Severity**: HIGH for Services/Handlers, MEDIUM for others

## Step 7: Documentation

Check if changes warrant doc updates:

| Change Type | Doc Needed |
|-------------|------------|
| New endpoint | README API table, OpenAPI |
| New module | README structure |
| New config | README config section |
| Breaking change | README, CHANGELOG |

```bash
# New endpoints?
git diff HEAD~1 -- "*.java" | grep -E "^\+.*@(GET|POST|PUT|DELETE)"
```

## Step 8: Output

Generate `self-review.json`:

```json
{
  "meta": { "commit": "abc123", "subject": "feat(auth): add login", "reviewedAt": "..." },
  "build": { "passed": true, "errors": 0 },
  "findings": [
    { "severity": "high", "category": "architecture", "file": "...", "rule": "...", "description": "..." }
  ],
  "summary": { "critical": 0, "high": 1, "medium": 2, "low": 0 },
  "grade": "B",
  "verdict": "Address 1 high issue before push"
}
```

## Step 9: Present Summary

```
## Self-Review: abc123 - feat(auth): add login

✓ Build passed
⚠ 1 high, 2 medium findings

### High
- [architecture] AuthHelper.java imports io.quarkus in engine-core

### Medium  
- [pattern] UserRequest.java is class, should be record
- [testing] AuthHelper.java missing test

### Verdict
⚠ Address high issue before push
```

## Grading

| Grade | Criteria |
|-------|----------|
| A | Build passes, no high findings, docs current |
| B | Build passes, ≤2 medium findings |
| C | Build passes, some gaps |
| D | Build passes, significant issues |
| F | Build fails OR critical findings |

## Auto-Fix Policy

**Apply automatically WITHOUT asking** (these are safe, mechanical fixes):
- Missing `final` on `@Inject` fields
- Missing `@Override` annotations
- Import organization and unused imports
- Trailing whitespace and formatting
- Missing newline at end of file
- Simple null checks where obvious

**Apply automatically THEN report** (fix and inform user):
- Concrete collection types → interface types (`ArrayList` → `List`)
- Raw types → parameterized types
- Missing `@Deprecated` javadoc reason
- Public → package-private for non-API classes

**Flag for write-docs skill** (cross-skill collaboration):
- New public API without documentation
- Changed endpoint signatures
- New configuration options
- Removed or renamed features

**Require human decision** (ask only for these):
- Architecture boundary violations (may be intentional)
- Missing tests for complex logic
- Design pattern choices
- Breaking API changes

## Collaboration with Other Skills

This skill shares findings with `premerge` and collaborates with:

| Finding Type | Collaborating Skill | Action |
|--------------|---------------------|--------|
| Missing docs for new API | `write-docs` | Auto-invoke to generate docs |
| Security anti-pattern | `security-review` | Flag for deep analysis |
| Missing tests | `test-coverage` | Get prioritization |
| SOLID violation | `solid-review` | Get refactoring suggestions |

**Collaboration Protocol:**
1. Write findings to `.review-output/self-review.json`
2. Include `collaborationNeeded` array for cross-skill issues
3. Other skills can read and respond to collaboration requests
4. `premerge` orchestrates the full collaboration flow

```json
{
  "collaborationNeeded": [
    {
      "skill": "write-docs",
      "reason": "New endpoint POST /api/matches needs documentation",
      "files": ["MatchResource.java"],
      "priority": "high"
    },
    {
      "skill": "security-review",
      "reason": "New auth filter added - needs security audit",
      "files": ["JwtAuthFilter.java"],
      "priority": "critical"
    }
  ]
}
```
