---
name: learn
description: "End-of-session reflection and knowledge capture. Summarizes the session, identifies key learnings, and updates CLAUDE.md. Triggers: 'learn', 'remember this', 'update claude.md', 'save this', end of productive session."
---

# Learn Skill

Capture what matters before context dies. Update CLAUDE.md for future sessions.

## Quick Flow

1. **Reflect**: What happened? What was discovered?
2. **Filter**: Is this worth remembering? (See criteria below)
3. **Read**: Check existing CLAUDE.md to avoid duplicates
4. **Propose**: Tell user what you plan to add
5. **Write**: Update CLAUDE.md after confirmation

## What's Worth Capturing

**Yes:**
- Non-obvious architecture ("routes are in ApiRouter.java, not controllers")
- User preferences ("prefers early returns over nested ifs")
- Gotchas discovered ("silently fails without REDIS_URL")
- Build/test quirks ("needs Docker running")
- Decisions & rationale ("chose X over Y because...")

**No:**
- Generic knowledge Claude already has
- One-off fixes
- Temporary workarounds
- Obvious things

**Rule**: Would future-you be glad to know this, or is it noise?

## CLAUDE.md Structure

```markdown
# CLAUDE.md

## Project Overview
[What this is, one paragraph]

## Architecture  
[Key decisions, component relationships]

## Development
[Build/test commands, common workflows]

## Code Style & Preferences
[User's preferences]

## Gotchas
[Things that'll bite you]

## Session Log
[Recent learnings, dated, newest first]
```

## Writing Style

Write like notes to a coworker:
- **Concise**: One line if possible
- **Specific**: `./gradlew bootRun` not "run gradle"
- **Honest**: Note if something's a workaround

```markdown
# Good
- `thunder-auth` silently fails if REDIS_URL unset. No error, just no session cache.

# Bad  
- Be careful with auth module configuration.
```

## Session Log Hygiene

- Date entries (newest first)
- After ~20 entries, consolidate old ones into permanent sections
- Remove entries that became obsolete
- Move repeated patterns into Architecture/Gotchas sections

```markdown
## Session Log

### 2025-01-31
- Hot-reload only works for `game-logic` module, not core
- Added WebSocket reconnection test (was missing)

### 2025-01-28
- Moved to MkDocs (simpler than Sphinx)
```

## Handling Conflicts

If new info contradicts existing CLAUDE.md:
1. Point out the conflict to user
2. Ask which is correct
3. Update with corrected info + date
4. Note in session log: "Corrected: X was wrong, actually Y"

## Output

End with:
1. What you added (brief)
2. The actual changes
3. "Want me to adjust anything?"
