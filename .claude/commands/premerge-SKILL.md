---
name: premerge
description: "Run all review skills (self, SOLID, test coverage, security, docs), merge findings, apply auto-fixes, generate unified summary. Triggers: 'premerge', 'pre-merge', 'full review', 'review everything', 'pre-push', 'check all'."
---

# Pre-Merge Review Skill

Orchestrate all reviews before merging. Fix what's fixable. Report the rest.

## What This Does

1. **Interactive scope selection** - Choose commits or review entire working tree
2. Run `self-review`, `solid-review`, `test-coverage`, `security-review`, `write-docs` with selected scope
3. Merge and deduplicate findings
4. Prioritize by severity × impact
5. Apply auto-fixable issues (with confirmation)
6. Verify/update documentation accuracy
7. Generate `summary.json`
8. Present unified verdict

## Workflow

### Step 0: Interactive Scope Selection

Present user with review scope options:

```bash
BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")
MAIN_BRANCH=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")

# Get commit options
COMMITS=$(git log --oneline ${MAIN_BRANCH}..HEAD 2>/dev/null | head -20)
COMMIT_COUNT=$(echo "$COMMITS" | wc -l | tr -d ' ')

# Get changed files in working tree
CHANGED_FILES=$(git diff --name-only 2>/dev/null)
CHANGED_COUNT=$(echo "$CHANGED_FILES" | grep -v '^$' | wc -l | tr -d ' ')

# Get untracked files
UNTRACKED=$(git ls-files --others --exclude-standard 2>/dev/null)
UNTRACKED_COUNT=$(echo "$UNTRACKED" | grep -v '^$' | wc -l | tr -d ' ')
```

**Interactive Prompt** (use AskUserQuestion tool):

```
What scope should we review?

Options:
1. "Last commit" - Review HEAD only
2. "Last N commits" - Review recent commits (specify N)
3. "All commits since ${MAIN_BRANCH}" - Review all ${COMMIT_COUNT} commits on this branch
4. "Changed files in working tree" - Review ${CHANGED_COUNT} modified files (unstaged/staged)
5. "Entire repository" - Full codebase review (slowest, most thorough)

Recommended: Option 3 for feature branches, Option 5 for initial reviews
```

Based on selection, set scope variables:

```bash
# Option 1: Last commit
SCOPE="commit"
SCOPE_TARGET="HEAD"
FILES=$(git diff-tree --no-commit-id --name-only -r HEAD)

# Option 2: Last N commits
SCOPE="commits"
SCOPE_TARGET="HEAD~N..HEAD"
FILES=$(git diff-tree --no-commit-id --name-only -r HEAD~N..HEAD)

# Option 3: All commits since main
SCOPE="branch"
SCOPE_TARGET="${MAIN_BRANCH}..HEAD"
FILES=$(git diff --name-only ${MAIN_BRANCH}..HEAD)

# Option 4: Changed files
SCOPE="working-tree"
SCOPE_TARGET="working-tree"
FILES=$(git diff --name-only && git ls-files --others --exclude-standard)

# Option 5: Entire repository
SCOPE="full"
SCOPE_TARGET="all"
FILES=$(find . -type f -name "*.java" -o -name "*.md" -o -name "*.properties" -o -name "*.yaml" | grep -v target | grep -v node_modules)
```

Write scope to temp file for skills to consume:

```bash
mkdir -p .review-output

cat > .review-output/scope.json <<EOF
{
  "scope": "${SCOPE}",
  "target": "${SCOPE_TARGET}",
  "files": [$(echo "$FILES" | sed 's/.*/"&"/' | paste -sd,)],
  "fileCount": $(echo "$FILES" | wc -l),
  "branch": "${BRANCH}",
  "mainBranch": "${MAIN_BRANCH}",
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
```

### Step 1: Context

Display selected scope:

```bash
SCOPE=$(jq -r '.scope' .review-output/scope.json)
TARGET=$(jq -r '.target' .review-output/scope.json)
FILE_COUNT=$(jq -r '.fileCount' .review-output/scope.json)
BRANCH=$(jq -r '.branch' .review-output/scope.json)

echo "=== Pre-Merge Review ==="
echo "Branch: $BRANCH"
echo "Scope: $SCOPE ($TARGET)"
echo "Files: $FILE_COUNT"
echo ""
```

### Step 2: Run Reviews

Execute each review skill with scope context. Each skill reads `.review-output/scope.json` to know what to analyze.

```bash
mkdir -p .review-output

# Pass scope to each skill via --scope-file flag or environment
export REVIEW_SCOPE_FILE=".review-output/scope.json"

# Run skills in parallel where possible
# Each skill reads scope.json to determine which files/commits to analyze
#
# Skills invoked with scope awareness:
#   self-review --scope-file scope.json
#   solid-review --scope-file scope.json
#   test-coverage --scope-file scope.json
#   security-review --scope-file scope.json
#   write-docs --scope-file scope.json
#
# Each writes to their respective JSON files:
#   self-review.json
#   solid-review.json
#   test-coverage.json
#   security-review.json
#   docs-review.json
```

**Scope-Aware Behavior:**
- **commit/commits/branch scope**: Only analyze files changed in selected commits
- **working-tree scope**: Only analyze modified and untracked files
- **full scope**: Analyze entire codebase

**Expected outputs:**
- `self-review.json` - commit hygiene, build, architecture, patterns (scoped)
- `solid-review.json` - SOLID violations (scoped)
- `test-coverage.json` - missing tests, quality (scoped)
- `security-review.json` - vulnerabilities, OWASP checks, secrets (scoped)
- `docs-review.json` - documentation accuracy, completeness (scoped)

### Step 2.5: Documentation Verification

Run `write-docs` to verify documentation accuracy:
- Checks if docs match actual implementation
- Detects outdated information
- Identifies missing documentation
- Optionally updates docs (with confirmation)

```bash
# Check documentation health
# - README.md current?
# - API docs match endpoints?
# - Architecture docs accurate?
# - Setup instructions work?
```

If docs are stale, offer to update them before continuing review.

### Step 3: Merge Findings

Collect all findings, deduplicate by file+rule:

```python
# Pseudocode
all_findings = []
seen = set()

for report in ['self-review.json', 'solid-review.json', 'test-coverage.json', 'security-review.json', 'docs-review.json']:
    data = json.load(report)
    for finding in data.get('findings', []):
        key = f"{finding['file']}:{finding.get('line','')}:{finding['rule']}"
        if key not in seen:
            all_findings.append(finding)
            seen.add(key)

# Sort by severity
severity_order = {'critical': 0, 'high': 1, 'medium': 2, 'low': 3}
all_findings.sort(key=lambda f: severity_order.get(f['severity'], 99))
```

### Step 4: Classify Auto-Fixable

| Issue | Auto-Fix | How |
|-------|----------|-----|
| Missing `final` on `@Inject` | ✓ | Add `final` modifier |
| Missing `@Override` | ✓ | Add annotation |
| Import organization | ✓ | Sort imports |
| Concrete collection types | ✓ | `ArrayList` → `List` |
| DTO class → record | ~ | Simple cases only |
| Outdated documentation | ✓ | Run write-docs to update |

**Cannot auto-fix:**
- Architecture violations (need design decision)
- Missing tests (need to write them)
- SOLID violations (need refactoring)

### Step 5: Apply Fixes

For each auto-fixable finding:

```markdown
Found 3 auto-fixable issues:
1. [MEDIUM] UserService.java:15 - Missing final on @Inject field
2. [MEDIUM] OrderService.java:8 - Missing final on @Inject field  
3. [LOW] PaymentService.java:22 - Missing @Override

Apply these fixes? [y/N]
```

If confirmed, apply fixes and re-run build to verify.

### Step 6: Generate summary.json

```json
{
  "meta": {
    "commit": "abc1234",
    "branch": "feature/auth",
    "reviewedAt": "2025-01-31T12:00:00Z",
    "skillsRun": ["self-review", "solid-review", "test-coverage", "security-review", "write-docs"]
  },
  "build": {
    "passed": true,
    "exitCode": 0
  },
  "findings": {
    "total": 15,
    "critical": 0,
    "high": 3,
    "medium": 8,
    "low": 4,
    "byCategory": {
      "architecture": 2,
      "solid": 5,
      "testing": 4,
      "documentation": 2,
      "patterns": 2,
      "security": 6
    }
  },
  "fixes": {
    "autoFixable": 3,
    "applied": 3,
    "remaining": 0
  },
  "coverage": {
    "testFilePercent": 85,
    "criticalMissing": ["PaymentService.java"]
  },
  "documentation": {
    "accuracy": 90,
    "completeness": 85,
    "staleFiles": ["README.md"],
    "missingDocs": ["control-plane service"]
  },
  "grades": {
    "overall": "B",
    "build": "A",
    "architecture": "A",
    "solid": "C",
    "testing": "B",
    "documentation": "B",
    "security": "C"
  },
  "topIssues": [
    {
      "priority": 1,
      "severity": "high",
      "category": "testing",
      "file": "PaymentService.java",
      "description": "Critical service missing tests",
      "action": "Add PaymentServiceTest.java"
    },
    {
      "priority": 2,
      "severity": "high",
      "category": "solid",
      "file": "UserService.java",
      "description": "SRP violation - 12 dependencies",
      "action": "Split into focused services"
    }
  ],
  "verdict": {
    "canPush": false,
    "blockers": ["1 critical service missing tests"],
    "warnings": ["SOLID violations in UserService"]
  }
}
```

### Step 7: Present Summary

```markdown
## Full Review Summary

**Commit**: abc1234 (feature/auth)  
**Overall Grade**: B

### Build
✓ Passed

### Findings
| Severity | Count |
|----------|-------|
| Critical | 0 |
| High | 3 |
| Medium | 8 |
| Low | 4 |

### Auto-Fixes Applied
✓ 3 fixes applied (missing final, @Override)

### By Category
| Category | Findings | Grade |
|----------|----------|-------|
| Architecture | 2 | A |
| SOLID | 5 | C |
| Testing | 4 | B |
| Documentation | 2 | B |
| Security | 6 | C |

### Top Issues to Address

1. **[HIGH/testing]** PaymentService.java missing tests
   → Add PaymentServiceTest.java

2. **[HIGH/solid]** UserService.java has 12 dependencies  
   → Split into UserAuthService + UserProfileService

3. **[HIGH/solid]** OrderProcessor uses instanceof chain
   → Refactor to strategy pattern

### Test Coverage
- File coverage: 85%
- Critical missing: PaymentService.java

### Documentation
- Accuracy: 90% (mostly correct)
- Completeness: 85% (minor gaps)
- Stale: README.md (mentions old endpoint)
- Missing: Control Plane service docs

### Verdict
⚠ **Address 1 blocker before push**
- PaymentService needs tests (critical business logic)

---
Report saved to `summary.json`
```

## Grading Logic

**Overall Grade** = weighted average:
- Build: 15% (pass/fail)
- Security: 25%
- Architecture: 15%
- SOLID: 15%
- Testing: 20%
- Documentation: 10%

**Can Push** criteria:
- Build passes
- No critical security findings
- No critical/high security vulnerabilities (injection, auth bypass)
- No hardcoded secrets in production code
- No high findings in architecture
- All critical services have tests
- Documentation is reasonably current (accuracy ≥80%)

## Arguments

- `--scope <commit|branch|working-tree|full>` - Pre-select scope (skip interactive prompt)
- `--commits <N>` - Review last N commits (implies --scope commits)
- `--since <ref>` - Review changes since ref (e.g., --since main)

**Examples:**
```bash
/premerge                    # Interactive scope selection
/premerge --scope full       # Full repo review
/premerge --commits 3        # Last 3 commits
/premerge --since main       # All changes since main
/premerge --scope working-tree  # Only modified files
```

## Integration with learn

After premerge review, if significant issues were found/fixed, suggest:

```
Want me to update CLAUDE.md with what we learned?
- Gotcha: PaymentService needs careful testing
- Pattern: UserService should be split
```

## Integration with write-docs

If documentation is stale or inaccurate (score <80%), prompt:

```
Documentation needs updates:
- README.md mentions deprecated endpoint
- Control Plane service not documented

Run write-docs to fix? [y/N]
```

If confirmed, run write-docs skill to update documentation before completing review.

## Scope Flow

```
┌─────────────────────┐
│  User invokes       │
│  /premerge          │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Interactive        │
│  Scope Selection    │
│  (or --scope flag)  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Write scope.json   │
│  with file list     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  Run skills in parallel:            │
│  • self-review (reads scope.json)   │
│  • solid-review (reads scope.json)  │
│  • test-coverage (reads scope.json) │
│  • security-review (reads scope.json)│
│  • write-docs (reads scope.json)    │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────┐
│  Merge findings     │
│  Apply fixes        │
│  Generate summary   │
└─────────────────────┘
```
