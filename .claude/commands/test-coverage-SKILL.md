---
name: test-coverage
description: "Analyze test coverage. Maps production files to tests, finds gaps, checks quality. Triggers: 'test coverage', 'missing tests', 'what needs tests'."
---

# Test Coverage Skill

Find missing tests. Prioritize by importance.

## Arguments

- `<path>` - Directory to analyze (default: `./`)
- `--scope-file <path>` - Path to scope.json (from premerge)
- `--changed` - Only files changed since main
- `--strict` - Exit 1 if coverage <80% or critical files untested

## Quick Flow

1. **Load scope** (if from premerge)
2. Find production files (scoped)
3. Map to expected test files
4. Classify missing tests by priority
5. Check test quality signals
6. Generate `test-coverage.json`

## Step 0: Load Scope

```bash
# Check if invoked from premerge (scope file exists)
if [[ -f .review-output/scope.json ]]; then
  SCOPE=$(jq -r '.scope' .review-output/scope.json)
  FILES=$(jq -r '.files[]' .review-output/scope.json)
  echo "Using scope: $SCOPE"

  # Filter to production Java files
  echo "$FILES" | grep "src/main/.*\.java$" | grep -v Test > /tmp/prod.txt

  # Filter to test files
  echo "$FILES" | grep "src/test/.*Test\.java$" > /tmp/tests.txt

elif [[ "$1" == "--changed" ]]; then
  # Legacy --changed flag
  MAIN=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")
  git diff $(git merge-base $MAIN HEAD) --name-only | grep "\.java$" | grep -v Test > /tmp/prod.txt
  git diff $(git merge-base $MAIN HEAD) --name-only | grep "Test\.java$" > /tmp/tests.txt
else
  # Default: analyze entire repo
  find . -path "*/src/main/*" -name "*.java" ! -name "*Test.java" > /tmp/prod.txt
  find . -path "*/src/test/*" -name "*Test.java" > /tmp/tests.txt
fi
```

## Step 1: Gather Files

Production and test files are now in `/tmp/prod.txt` and `/tmp/tests.txt` based on scope.

## Step 2: Map Production → Test

| Production | Expected Test |
|------------|---------------|
| `src/main/.../Foo.java` | `src/test/.../FooTest.java` |
| `FooServiceImpl.java` | `FooServiceImplTest.java` |

```bash
for f in $(cat /tmp/prod.txt); do
  test=$(echo "$f" | sed 's|/main/|/test/|; s|\.java$|Test.java|')
  [[ -f "$test" ]] && echo "✓ $f" || echo "✗ $f → $test"
done
```

## Step 3: Priority Classification

| File Type | Priority | Rationale |
|-----------|----------|-----------|
| `*Service.java` | CRITICAL | Business logic |
| `*ServiceImpl.java` | CRITICAL | Implementation |
| `*Repository.java` | HIGH | Data access |
| `*Resource.java` | HIGH | API endpoints |
| `*Handler.java` | HIGH | Event handling |
| `*Mapper.java` | MEDIUM | Transformation |
| `*Validator.java` | MEDIUM | Validation |
| `*Factory.java` | MEDIUM | Creation |
| `*Config.java` | LOW | Config wiring |
| `*Exception.java` | LOW | Simple classes |
| `*Dto/Request/Response.java` | LOW | Data classes |
| `*Constants.java` | SKIP | No behavior |

## Step 4: Test Quality

Check existing tests for quality signals:

| Signal | Good | Warning |
|--------|------|---------|
| Assertions per test | ≥1 | 0 |
| Naming | `should_*`, `when_*` | `test1`, `testIt` |
| Length | <50 lines | >50 lines |

```bash
for t in $(cat /tmp/tests.txt | head -30); do
  tests=$(grep -c "@Test" "$t")
  asserts=$(grep -cE "assert|verify|expect" "$t")
  [[ $asserts -eq 0 ]] && echo "⚠ $t - no assertions"
  [[ $tests -gt 0 && $((asserts/tests)) -lt 1 ]] && echo "⚠ $t - low assertion ratio"
done
```

## Step 5: Output

Generate `test-coverage.json`:

```json
{
  "meta": { "path": "./", "mode": "full", "analyzedAt": "..." },
  "summary": {
    "productionFiles": 100,
    "filesWithTests": 85,
    "coveragePercent": 85
  },
  "missing": {
    "critical": ["UserService.java", "PaymentService.java"],
    "high": ["UserRepository.java"],
    "medium": ["UserMapper.java"],
    "low": ["UserConfig.java"]
  },
  "quality": {
    "warnings": [
      { "file": "UserServiceTest.java", "issue": "no assertions" }
    ]
  },
  "grade": "B",
  "strictPassed": false
}
```

## Present Summary

```
## Test Coverage

**Coverage**: 85/100 (85%) | **Grade**: B

### Missing Tests by Priority

| Priority | Count | Files |
|----------|-------|-------|
| CRITICAL | 2 | UserService, PaymentService |
| HIGH | 1 | UserRepository |
| MEDIUM | 3 | ... |
| LOW | 5 | ... |

### Quality Warnings
- ⚠ UserServiceTest.java - no assertions
- ⚠ OrderTest.java - poorly named tests

### Recommendations
1. **[CRITICAL]** Add UserServiceTest.java
2. **[CRITICAL]** Add PaymentServiceTest.java
3. **[HIGH]** Add UserRepositoryTest.java

### Verdict
⚠ Critical files need tests
```

## Grading

| Grade | File Coverage | Critical Missing |
|-------|---------------|------------------|
| A | ≥90% | 0 |
| B | ≥80% | ≤1 |
| C | ≥70% | ≤3 |
| D | ≥60% | any |
| F | <60% | any |

## Strict Mode

```bash
coverage=$(cat /tmp/coverage-pct.txt)
critical=$(cat /tmp/critical-missing.txt | wc -l)

[[ $coverage -lt 80 ]] && exit 1
[[ $critical -gt 0 ]] && exit 1
exit 0
```

## Autonomous Operation

This skill operates **autonomously** with the following behavior:

**Auto-generate when possible:**
| Scenario | Action |
|----------|--------|
| Missing test file for simple DTO | Generate basic test skeleton |
| Test file exists but empty | Add TODO comments with suggested tests |
| Test renamed but exists | Update mapping, no warning |

**Cannot auto-fix (report only):**
| Scenario | Why |
|----------|-----|
| Complex service missing tests | Needs understanding of business logic |
| Integration tests missing | Architecture decision needed |
| Security-critical tests | Requires security expertise |

## Collaboration with Other Skills

### Receiving Collaboration Requests

When other skills flag testing needs:
```json
{
  "collaborationNeeded": [
    {
      "skill": "test-coverage",
      "reason": "Security vulnerability needs regression test",
      "files": ["UserRepository.java"],
      "testType": "security",
      "priority": "critical"
    }
  ]
}
```

**Response Actions:**
| Request Source | Testing Action |
|----------------|----------------|
| security-review: vulnerability fix | Prioritize security test coverage |
| self-review: new feature | Flag if tests missing |
| solid-review: refactored class | Verify test mapping still works |

### Sending Collaboration Requests

When test analysis reveals broader issues:
```json
{
  "collaborationRequests": [
    {
      "targetSkill": "write-docs",
      "reason": "Test requirements should be documented",
      "suggestedContent": {
        "section": "testing.md",
        "topics": ["Coverage requirements", "Test patterns"]
      }
    },
    {
      "targetSkill": "self-review",
      "reason": "Untested critical service - block merge?",
      "files": ["PaymentService.java"],
      "action": "WARN_OR_BLOCK"
    }
  ]
}
```

### Prioritization from Collaboration

When security-review flags vulnerabilities, this skill:
1. Elevates those files to CRITICAL priority
2. Adds "security" tag to test recommendations
3. Suggests specific test cases for vulnerability regression
