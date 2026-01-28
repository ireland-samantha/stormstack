# Self-Review Skill

Perform a comprehensive self-review of the current commit against CLAUDE.md guidelines.

## Instructions

1. **Read CLAUDE.md** to understand all code quality guidelines (especially guidelines 1-13)

2. **Get commit details** using `git show --stat HEAD`

3. **Review key changed files** against each guideline:
   - Guideline 1: No @Deprecated annotations
   - Guideline 2: Fluent API preference
   - Guideline 3: No production changes for testing
   - Guideline 4: No unnecessary getters/setters
   - Guideline 5: Build verification
   - Guideline 6: Use web-api-adapter for API calls
   - Guideline 7: DTOs for >3 parameters
   - Guideline 8: Test fixtures for business objects
   - Guideline 9: Always run tests
   - Guideline 10: Never manually parse JSON
   - Guideline 11: Follow SOLID principles
   - Guideline 12: No magic numbers
   - Guideline 13: Self-review practice (performance test tags, leaky abstractions, constructor params, test coverage)

4. **Evaluate test coverage** by checking which new production files have corresponding test files

5. **Create self-review.json** in the project root with the exact format from CLAUDE.md guideline 13:
   ```json
   {
     "review": { "commit": "...", "date": "..." },
     "findings": [{ "severity": "low|medium|high", "guideline": "...", "file": "...", "line": N, "description": "...", "recommendation": "..." }],
     "testCoverage": { "summary": "...", "estimatedCoverage": "..." },
     "grade": { "overall": "A-F", "breakdown": { "codeQuality": "...", "testCoverage": "...", "guidelineAdherence": "..." } },
     "recommendations": [{ "priority": "high|medium|low", "action": "...", "reason": "..." }]
   }
   ```

6. **Address high-priority feedback** from the recommendations immediately

7. **Report summary** to the user including:
   - Overall grade
   - Number of findings by severity
   - High-priority items that were fixed
   - Remaining medium/low items for consideration

## Key Checks

- `@Tag("performance")` on all performance test classes
- Named constants for all magic numbers (both Java and TypeScript/JavaScript)
- No `getDelegate()` or `getInternal*()` methods exposing implementation details
- Constructor parameter count <= 3, or use config DTOs
- All new production code has corresponding tests
