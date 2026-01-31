---
name: self-review
description: "Review git commits against project guidelines. Checks build, architecture, patterns, tests, docs. Triggers: 'review my commit', 'self-review', 'check my changes', 'pre-push review'."
---

# Self-Review Skill

Review commits against CLAUDE.md guidelines. Fast feedback before push.

## Quick Flow

1. **Load scope** (from `.review-output/scope.json` if exists, else default to HEAD)
2. Load CLAUDE.md for project-specific rules
3. Gather commit/file info based on scope
4. Run build (stop if fails)
5. Check architecture boundaries
6. Detect anti-patterns
7. Verify test coverage (delegate to test-coverage)
8. Check documentation needs
9. Generate `self-review.json`
10. Present summary with verdict

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

**If build fails**: Stop. Present errors. Offer to diagnose.

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

## Auto-Fixable

Offer to fix:
- Missing `final` on `@Inject`
- Missing `@Override`
- Import organization

Require manual:
- Architecture violations
- Missing tests
- Missing docs
