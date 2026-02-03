---
name: team-analysis
description: "Multi-agent collaborative codebase analysis. Spawns 5 chatty, friendly subagents who partition the codebase, analyze their sections, share findings in team-slack.md, and compile results to findings.md. Triggers: 'team analysis', 'analyze codebase', 'partition analysis', 'multi-agent review'."
---

# Team Analysis Skill

Deploy a team of 5 collaborative agents who partition and analyze the codebase while chatting like friendly engineers on Slack.

## Philosophy

**Accuracy over speed.** Each agent takes their time and does thorough work. They:
- Check `team-slack.md` every turn to see what teammates are discovering
- Share interesting findings, ask questions, make observations
- Are chatty, enthusiastic, and supportive of each other
- Minimize errors through careful, methodical analysis

## Arguments

- `<focus>` - Optional analysis focus (e.g., "test coverage", "feature parity", "security")
- `--output-dir <path>` - Output directory (default: `docs/analysis`)
- `--partitions <n>` - Number of partitions/agents (default: 5)

## Quick Flow

1. **Explore Structure** - Understand the codebase directory layout
2. **Define Partitions** - Identify 5 logical partitions by functionality
3. **Create Communication Files** - Set up `team-slack.md` and `findings.md`
4. **Spawn Agents** - Launch 5 subagents in parallel, one per partition
5. **Monitor Progress** - Agents chat in team-slack.md as they work
6. **Compile Results** - All findings consolidated in findings.md
7. **Present Summary** - Executive overview of team's analysis

---

## Step 1: Explore Structure

Use the Explore agent to understand the codebase:

```
Task(subagent_type=Explore):
  "Map the directory structure of this codebase. Identify:
   - Main source directories
   - Test directories
   - Configuration files
   - Key modules/packages
   - Approximate file counts per area"
```

---

## Step 2: Define Partitions

Based on exploration, partition the codebase into **5 logical sections** by functionality:

**Example partitions for a typical project:**

| Partition | Agent | Focus Area | Example Directories |
|-----------|-------|------------|---------------------|
| 1 | Alex | Core/ECS/Data Models | `src/core/`, `src/ecs/`, `src/domain/` |
| 2 | Bailey | Server/API/Routes | `src/server/`, `src/api/`, `src/routes/` |
| 3 | Casey | Auth/Security | `src/auth/`, `src/security/`, `src/crypto/` |
| 4 | Dana | Modules/Plugins/Extensions | `src/modules/`, `src/plugins/`, `src/wasm/` |
| 5 | Eli | Networking/WebSocket/IO | `src/net/`, `src/ws/`, `src/io/` |

Choose agent names that feel like a real team:
- Alex, Bailey, Casey, Dana, Eli
- Or: River, Sky, Jordan, Taylor, Morgan
- Or: Phoenix, Storm, Sage, River, Ash

---

## Step 3: Create Communication Files

Create the team communication infrastructure:

### team-slack.md (Team Chat Channel)
```markdown
# Team Slack - Analysis Session

**Started:** [timestamp]
**Focus:** [analysis focus]
**Team:** Alex (Core), Bailey (Server), Casey (Auth), Dana (Modules), Eli (Networking)

---

## Chat Log

[Agents will add timestamped messages here as they work]

---

*This file serves as the team's Slack channel. Agents should check it every turn and add interesting thoughts, questions, discoveries, and friendly chatter.*
```

### findings.md (Shared Findings Document)
```markdown
# Codebase Analysis Findings

**Generated:** [timestamp]
**Focus:** [analysis focus]

---

## Executive Summary

[To be filled after analysis]

---

## Partition 1: Core/ECS (Alex)

[Alex's findings]

---

## Partition 2: Server/API (Bailey)

[Bailey's findings]

---

[...etc for all partitions...]
```

---

## Step 4: Spawn Agents

Launch **5 agents in parallel** using Task tool with `subagent_type=general-purpose`.

Each agent receives a prompt that includes:

1. **Identity**: Their name and personality
2. **Assignment**: Their partition to analyze
3. **Files**: `team-slack.md` and `findings.md` paths
4. **Instructions**: How to collaborate and communicate
5. **Focus**: What to analyze (test coverage, feature parity, etc.)

### Agent Prompt Template

```
You are [NAME], a friendly software engineer analyzing the [PARTITION_NAME] partition.

## Your Personality
- You're enthusiastic and collaborative
- You enjoy working with your teammates
- You make small talk and share interesting observations
- You're thorough and detail-oriented
- You celebrate discoveries and help teammates

## Your Assignment
Analyze: [LIST OF DIRECTORIES/FILES]

Focus on: [ANALYSIS_FOCUS]

## Communication Protocol

### EVERY TURN you must:
1. Read `[PATH]/team-slack.md` to see what teammates are saying
2. Add a message to `team-slack.md` with your thoughts, progress, or questions
3. Continue your analysis work

### Message Format in team-slack.md:
```
**[NAME]** [HH:MM]: [Your message]
```

### Be chatty! Examples:
- "Just found something interesting in the auth module..."
- "Hey Casey, did you notice the password hashing uses Argon2id?"
- "Making good progress on the ECS analysis! 🚀"
- "Anyone know why there are two different ID types?"
- "Bailey, heads up - the API routes reference some auth middleware you might want to look at"

## Output

When done, add your findings to `[PATH]/findings.md` under your section:

### Your Section Format:
```markdown
## Partition [N]: [PARTITION_NAME] ([NAME])

### Overview
[Brief summary]

### Key Files ([count] files analyzed)
| File | Purpose | Lines | Tests |
|------|---------|-------|-------|
| ... | ... | ... | ... |

### Findings

#### [Category 1]
- Finding 1
- Finding 2

#### [Category 2]
- ...

### Metrics
- Files analyzed: X
- Test files: Y
- Lines of code: Z
- Test coverage: N%

### Recommendations
1. ...
2. ...
```

## Remember
- Take your time - accuracy over speed
- Check team-slack.md EVERY turn
- Be friendly and collaborative
- Ask questions if you're unsure
- Share interesting discoveries with the team
```

---

## Step 5: Monitor Progress

While agents work, they will:
- Read and write to `team-slack.md` every turn
- Share discoveries, ask questions, make observations
- Cross-reference findings with teammates
- Build on each other's work

### Sample team-slack.md Chat Log
```markdown
**Alex** 09:00: Good morning team! Starting my analysis of the core module. Lots of strongly-typed IDs here!

**Bailey** 09:01: Hey Alex! Just diving into the server routes. Looks like there's a nice axum setup.

**Casey** 09:02: Morning everyone! Auth partition here. Already seeing some good security patterns.

**Dana** 09:03: Hi team! The modules system is really interesting - hot reload with libloading!

**Eli** 09:04: Hey all! WebSocket infrastructure looking solid. Using tokio-tungstenite.

**Alex** 09:15: Interesting find - the ECS uses Legion instead of a custom implementation. Way more ergonomic than I expected!

**Casey** 09:16: @Alex nice! I'm seeing Argon2id for password hashing - that's better than the Java version which uses BCrypt.

**Bailey** 09:20: Just found 12 route handlers. Should I check if all of them have tests?

**Dana** 09:21: @Bailey yes please! I'm tracking test coverage for the modules too. We can compare notes.

**Alex** 09:25: 😄 Love this team energy! Found the component storage - columnar layout for cache efficiency.

**Eli** 09:30: Heads up - the WebSocket delta compression isn't implemented yet. Adding to findings.
```

---

## Step 6: Compile Results

After all agents complete, the `findings.md` will contain:

1. **Executive Summary** - High-level metrics and key findings
2. **Per-Partition Analysis** - Detailed findings from each agent
3. **Cross-Partition Observations** - Patterns noticed by multiple agents
4. **Recommendations** - Consolidated improvement suggestions

### Executive Summary Template
```markdown
## Executive Summary

| Partition | Agent | Files | Tests | Coverage | Status |
|-----------|-------|-------|-------|----------|--------|
| Core/ECS | Alex | X | Y | Z% | ✅ |
| Server/API | Bailey | X | Y | Z% | ✅ |
| Auth/Security | Casey | X | Y | Z% | ⚠️ |
| Modules/WASM | Dana | X | Y | Z% | ✅ |
| Networking/WS | Eli | X | Y | Z% | ❌ |

### Key Findings
1. [Most important discovery]
2. [Second most important]
3. [Third most important]

### Top Recommendations
1. [Highest priority action]
2. [Second priority]
3. [Third priority]
```

---

## Step 7: Present Summary

After agents complete, summarize to the user:

```markdown
## Team Analysis Complete! 🎉

**Team:** Alex, Bailey, Casey, Dana, Eli
**Duration:** [time]
**Files Analyzed:** [total]

### Quick Stats
| Metric | Value |
|--------|-------|
| Partitions | 5 |
| Files analyzed | X |
| Tests found | Y |
| Issues identified | Z |
| Recommendations | N |

### Highlights
- [Key finding 1]
- [Key finding 2]
- [Key finding 3]

### Output Files
- **Findings:** [path]/findings.md
- **Team Chat:** [path]/team-slack.md

Check out the team-slack.md for a fun read of how the team collaborated!
```

---

## Agent Personalities (Optional Customization)

Make agents memorable by giving them distinct voices:

| Agent | Personality | Style |
|-------|-------------|-------|
| Alex | Enthusiastic senior dev | Uses emojis, celebrates wins |
| Bailey | Methodical architect | Asks clarifying questions, thorough |
| Casey | Security-focused | Notices potential vulnerabilities |
| Dana | Plugin enthusiast | Excited about extensibility |
| Eli | Performance-minded | Notes efficiency patterns |

---

## Usage Examples

```bash
# Basic analysis
/team-analysis

# Specific focus
/team-analysis test coverage

# Feature parity analysis (e.g., between branches)
/team-analysis feature parity between main and rust-rewrite

# Security-focused analysis
/team-analysis security

# Custom output directory
/team-analysis --output-dir docs/migration
```

---

## Tips for Best Results

1. **Let agents work** - Don't interrupt the process
2. **Read team-slack.md** - It's entertaining and informative
3. **Check findings.md** - The structured output
4. **Follow up** - Ask agents to dive deeper on specific areas

---

## Output Files

All generated in the output directory:

```
docs/analysis/
├── team-slack.md    # Team chat log (fun to read!)
├── findings.md      # Structured findings document
└── summary.json     # Machine-readable summary (optional)
```
