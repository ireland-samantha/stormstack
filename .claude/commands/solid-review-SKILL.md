---
name: solid-review
description: "Analyze codebase for SOLID principle violations. Triggers: 'SOLID review', 'architecture review', 'check design', 'find violations'."
---

# SOLID Review Skill

Find SOLID violations. Produce actionable report.

## Arguments

- `<path>` - Directory to analyze (default: `src/main/java`)
- `--scope-file <path>` - Path to scope.json (from premerge)
- `--changed` - Only files changed since main
- `--principle SRP|OCP|LSP|ISP|DIP` - Focus on one principle

## Step 0: Load Scope

```bash
# Check if invoked from premerge (scope file exists)
if [[ -f .review-output/scope.json ]]; then
  SCOPE=$(jq -r '.scope' .review-output/scope.json)
  FILES=$(jq -r '.files[]' .review-output/scope.json | grep "\.java$")
  echo "Using scope: $SCOPE ($(echo "$FILES" | wc -l) Java files)"
  echo "$FILES" > /tmp/files.txt
elif [[ "$1" == "--changed" ]]; then
  # Legacy --changed flag
  MAIN=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")
  git diff --name-only $(git merge-base $MAIN HEAD) --name-only -- "*.java" > /tmp/files.txt
else
  # Default: analyze src/main/java
  find ${1:-src/main/java} -name "*.java" > /tmp/files.txt
fi
```

## Thresholds

Read from CLAUDE.md `## SOLID Thresholds` section, or use defaults:

| Metric | Medium | High |
|--------|--------|------|
| Lines per class | 300 | 500 |
| Public methods | 10 | 15 |
| Dependencies (@Inject) | 5 | 8 |
| Interface methods | 7 | 10 |
| instanceof checks | 2 | 4 |

## Principles & Detection

### S - Single Responsibility

**Violation**: Class has multiple reasons to change.

```bash
for f in $(cat /tmp/files.txt); do
  lines=$(wc -l < "$f")
  methods=$(grep -c "public.*(" "$f")
  injects=$(grep -c "@Inject" "$f")
  
  [[ $lines -gt 500 ]] && echo "HIGH: $f - $lines lines"
  [[ $methods -gt 15 ]] && echo "HIGH: $f - $methods public methods"
  [[ $injects -gt 8 ]] && echo "HIGH: $f - $injects dependencies"
done
```

**Fixes**: Extract class, extract module, facade pattern

### O - Open/Closed

**Violation**: Must modify code to add behavior.

```bash
for f in $(cat /tmp/files.txt); do
  instanceof=$(grep -c "instanceof" "$f")
  switches=$(grep -c "switch\s*(" "$f")
  elseif=$(grep -c "else if" "$f")
  
  [[ $instanceof -gt 4 ]] && echo "HIGH: $f - $instanceof instanceof"
  [[ $switches -gt 2 ]] && echo "MEDIUM: $f - $switches switches"
  [[ $elseif -gt 4 ]] && echo "MEDIUM: $f - $elseif else-if chain"
done
```

**Fixes**: Strategy pattern, factory pattern, plugin architecture

### L - Liskov Substitution

**Violation**: Subclass breaks parent's contract.

```bash
for f in $(cat /tmp/files.txt); do
  # Override that throws
  grep -A5 "@Override" "$f" | grep -q "UnsupportedOperationException" && \
    echo "HIGH: $f - Override throws UnsupportedOperationException"
  
  # Override returns null
  grep -A10 "@Override" "$f" | grep -q "return null" && \
    echo "MEDIUM: $f - Override returns null"
done
```

**Fixes**: Composition over inheritance, interface extraction

### I - Interface Segregation

**Violation**: Clients depend on methods they don't use.

```bash
for f in $(cat /tmp/files.txt); do
  if grep -q "^public interface" "$f"; then
    methods=$(grep -cE "^\s+\w+.*\(.*\);" "$f")
    [[ $methods -gt 10 ]] && echo "HIGH: $f - $methods methods (fat interface)"
  fi
  
  # Empty implementations
  if grep -q "implements" "$f"; then
    empty=$(grep -cE "\{\s*\}" "$f")
    [[ $empty -gt 0 ]] && echo "HIGH: $f - $empty empty method bodies"
  fi
done
```

**Fixes**: Split interface, adapter pattern

### D - Dependency Inversion

**Violation**: Depends on concretions, not abstractions.

```bash
for f in $(cat /tmp/files.txt); do
  # Direct instantiation
  grep -n "new \w\+\(Service\|Repository\|Client\)\s*(" "$f" && \
    echo "HIGH: $f - Direct instantiation"
  
  # Concrete collections
  grep -n "private\s\+\(ArrayList\|HashMap\)\s*<" "$f" && \
    echo "MEDIUM: $f - Concrete collection type"
  
  # Framework in core
  if [[ "$f" == *"engine-core"* ]]; then
    grep -n "import io\.quarkus\|import jakarta\." "$f" | grep -v validation && \
      echo "HIGH: $f - Framework import in core"
  fi
done
```

**Fixes**: Dependency injection, interface extraction

## Output

Generate `solid-review.json`:

```json
{
  "meta": { "path": "src/main/java", "filesAnalyzed": 50, "analyzedAt": "..." },
  "summary": {
    "SRP": { "high": 1, "medium": 2 },
    "OCP": { "high": 0, "medium": 1 },
    "LSP": { "high": 0, "medium": 0 },
    "ISP": { "high": 1, "medium": 0 },
    "DIP": { "high": 2, "medium": 3 }
  },
  "findings": [...],
  "hotspots": [
    { "file": "UserService.java", "violations": ["SRP", "DIP"], "priority": 1 }
  ],
  "grade": "C"
}
```

## Present Summary

```
## SOLID Review

**Files**: 50 | **Grade**: C

| Principle | High | Medium |
|-----------|------|--------|
| Single Responsibility | 1 | 2 |
| Open/Closed | 0 | 1 |
| Liskov Substitution | 0 | 0 |
| Interface Segregation | 1 | 0 |
| Dependency Inversion | 2 | 3 |

### Hotspots (priority refactor)
1. **UserService.java** - SRP, DIP (3 findings)
2. **OrderProcessor.java** - OCP (2 findings)

### Top Fixes
1. [DIP] Extract interface for PaymentService
2. [SRP] Split UserService → UserAuthService + UserProfileService
3. [OCP] Replace type switch in OrderProcessor with strategy
```

## Grading

| Grade | Criteria |
|-------|----------|
| A | No high, ≤5 medium |
| B | No high, ≤10 medium |
| C | ≤2 high, ≤15 medium |
| D | ≤5 high |
| F | >5 high |
