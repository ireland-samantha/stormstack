---
name: security-review
description: "Comprehensive security analysis: code deep dive, vulnerability detection, OWASP Top 10 checks, secrets scanning, dependency audit. Triggers: 'security review', 'check security', 'find vulnerabilities', 'security audit'."
---

# Security Review Skill

Deep security analysis with code exploration, vulnerability detection, and project-specific best practices.

## Arguments

- `<path>` - Directory to analyze (default: `.`)
- `--scope-file <path>` - Path to scope.json (from premerge)
- `--changed` - Only files changed since main
- `--fetch-reports` - Fetch recent CVE/security advisories
- `--deep` - Enable exhaustive analysis (slower, more thorough)

## Quick Flow

1. **Load Scope** - Determine which files/commits to analyze
2. **Load Security Best Practices** - Read project-specific guidelines
3. **Code Deep Dive** - Explore security-sensitive areas (scoped)
4. **Fetch Security Reports** - Get recent CVEs, dependency vulnerabilities
5. **OWASP Top 10 Checks** - Detect common web vulnerabilities (scoped)
6. **Secrets Detection** - Find hardcoded credentials, keys, tokens (scoped)
7. **Authentication/Authorization** - Review auth patterns (scoped)
8. **Input Validation** - Check request validation (scoped)
9. **Dependency Security** - Audit third-party libraries
10. **Crypto Usage** - Review cryptographic implementations (scoped)
11. **Generate Report** - Create `security-review.json` with findings
12. **Present Summary** - Actionable security recommendations

---

## Step -1: Load Scope

```bash
# Check if invoked from premerge (scope file exists)
if [[ -f .review-output/scope.json ]]; then
  SCOPE=$(jq -r '.scope' .review-output/scope.json)
  FILES=$(jq -r '.files[]' .review-output/scope.json)
  FILE_COUNT=$(echo "$FILES" | wc -l)
  echo "Using scope: $SCOPE ($FILE_COUNT files)"

  # Write scoped files to temp files for checks
  echo "$FILES" | grep "\.java$" > /tmp/java-files.txt
  echo "$FILES" | grep -E "\.(properties|yaml|yml|env)$" > /tmp/config-files.txt

  # Set scope flag for checks
  SCOPED=true

elif [[ "$1" == "--changed" ]]; then
  # Legacy --changed flag
  MAIN=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "main")
  git diff --name-only $(git merge-base $MAIN HEAD) > /tmp/all-files.txt
  cat /tmp/all-files.txt | grep "\.java$" > /tmp/java-files.txt
  cat /tmp/all-files.txt | grep -E "\.(properties|yaml|yml|env)$" > /tmp/config-files.txt
  SCOPED=true

else
  # Default: analyze entire repo
  find . -type f -name "*.java" | grep -v target > /tmp/java-files.txt
  find . -type f \( -name "*.properties" -o -name "*.yaml" -o -name "*.yml" -o -name "*.env" \) > /tmp/config-files.txt
  SCOPED=false
fi

# Adjust checks based on scope
if [[ "$SCOPED" == "true" ]]; then
  echo "Running focused security checks on $(cat /tmp/java-files.txt | wc -l) Java files"
else
  echo "Running full security scan"
fi
```

---

## Step 0: Load Security Best Practices

Read project-specific security guidelines from `references/security-best-practices.md` for context on:
- Lightning Engine-specific threats (module isolation, command queue, WebSocket)
- Project security patterns and anti-patterns
- Validation requirements and examples

## Step 1: Code Deep Dive

Explore codebase to understand security architecture before running checks.

### Security-Sensitive Areas to Explore

```bash
# Use scoped files from Step -1 if available, otherwise search entire repo
if [[ -f /tmp/java-files.txt ]]; then
  SEARCH_FILES=$(cat /tmp/java-files.txt)
else
  SEARCH_FILES=$(find . -type f -name "*.java" | grep -v target)
fi

# Authentication & Authorization
echo "$SEARCH_FILES" | xargs grep -l "authenticate\|authorize\|JWT\|BCrypt\|login\|password" 2>/dev/null > /tmp/auth-files.txt || touch /tmp/auth-files.txt

# API endpoints (entry points)
echo "$SEARCH_FILES" | xargs grep -l "@Path\|@GET\|@POST\|@PUT\|@DELETE" 2>/dev/null > /tmp/api-files.txt || touch /tmp/api-files.txt

# Database access
echo "$SEARCH_FILES" | xargs grep -l "Repository\|MongoClient\|executeQuery\|createQuery" 2>/dev/null > /tmp/db-files.txt || touch /tmp/db-files.txt

# WebSocket endpoints
echo "$SEARCH_FILES" | xargs grep -l "@ServerEndpoint\|WebSocket" 2>/dev/null > /tmp/websocket-files.txt || touch /tmp/websocket-files.txt

# Command handlers (user input)
echo "$SEARCH_FILES" | grep -E "Handler\.java$|Command\.java$" > /tmp/command-files.txt || touch /tmp/command-files.txt

# Config files already in /tmp/config-files.txt from scope
```

### Deep Dive Analysis

Use Task tool with Explore agent to understand:
- Authentication flow (JWT generation, validation, storage)
- Authorization checks (role-based access control)
- Input validation patterns across API endpoints
- Database query construction methods
- WebSocket authentication/session management
- Module loading security (ClassLoader isolation)
- Command queue validation

---

## Step 2: Fetch Security Reports

### Recent CVE Checks

```bash
# Check if --fetch-reports flag is set
if [[ "$1" == "--fetch-reports" ]]; then
  echo "Fetching recent security advisories..."

  # Use WebSearch to find recent Java/Quarkus CVEs
  # Store results in /tmp/cve-reports.txt
fi
```

### Dependency Vulnerability Scan

```bash
# Maven dependency check
if command -v mvn &> /dev/null; then
  echo "Running OWASP Dependency Check..."
  mvn org.owasp:dependency-check-maven:check -DskipTests 2>&1 | tee /tmp/dependency-check.log

  # Parse results
  if [[ -f target/dependency-check-report.html ]]; then
    grep -oP "severity[^<]+" target/dependency-check-report.html | sort | uniq -c > /tmp/vuln-summary.txt
  fi
fi
```

### Known Vulnerabilities Database

Check dependencies against known vulnerable versions:
- Log4j < 2.17.1 (Log4Shell)
- Spring Framework RCE vulnerabilities
- Jackson deserialization issues
- Netty, Undertow security patches
- MongoDB driver vulnerabilities

---

## Step 3: OWASP Top 10 Checks

### A01:2021 - Broken Access Control

**Check 1: Missing Authorization Checks**
```bash
# Find endpoints without @RolesAllowed or auth checks
for f in $(cat /tmp/api-files.txt); do
  if grep -q "@Path" "$f"; then
    if ! grep -q "@RolesAllowed\|@Authenticated\|securityContext.isUserInRole" "$f"; then
      echo "HIGH: $f - Endpoint missing authorization check"
    fi
  fi
done
```

**Check 2: Direct Object Reference**
```bash
# Find UUID/ID path params without ownership validation
grep -rn "@PathParam.*UUID\|@PathParam.*id" --include="*.java" | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  # Check if file validates ownership
  if ! grep -q "validateOwnership\|checkPermission\|belongsToUser" "$file"; then
    echo "HIGH: $file - Possible IDOR vulnerability"
  fi
done
```

### A02:2021 - Cryptographic Failures

**Check 1: Weak Password Hashing**
```bash
grep -rn "MessageDigest\|MD5\|SHA1" --include="*.java" . && \
  echo "HIGH: Weak hashing algorithm detected (use BCrypt/Argon2)"

# Verify BCrypt cost factor
grep -rn "BCrypt" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  grep -A5 -B5 "BCrypt" "$file" | grep -oP "cost.*\d+"
done
```

**Check 2: Hardcoded Secrets**
```bash
# Secret patterns
grep -rn --include="*.java" --include="*.properties" --include="*.yaml" \
  -E "(password|secret|api[_-]?key|private[_-]?key|token|jwt[_-]?secret)\s*=\s*['\"][^'\"]+['\"]" . \
  | grep -v "CHANGEME\|YOUR_" \
  > /tmp/hardcoded-secrets.txt

# AWS keys
grep -rn --include="*.java" --include="*.properties" --include="*.yaml" \
  -E "AKIA[0-9A-Z]{16}" . >> /tmp/hardcoded-secrets.txt

# JWT secrets
grep -rn "jwt.secret\|jwt-secret" --include="*.properties" --include="*.yaml" . | \
  grep -v "\${" >> /tmp/hardcoded-secrets.txt
```

**Check 3: Insecure TLS/SSL**
```bash
grep -rn "SSLContext\|TrustManager" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if grep -q "TrustAllCertificates\|X509TrustManager.*{}" "$file"; then
    echo "CRITICAL: $file - Insecure TLS configuration (trusts all certificates)"
  fi
done
```

### A03:2021 - Injection

**Check 1: SQL Injection**
```bash
# Find query string concatenation
grep -rn --include="*.java" \
  -E "executeQuery\s*\(.*\+|createQuery\s*\(.*\+" . \
  > /tmp/sql-injection.txt

# MongoDB query injection
grep -rn --include="*.java" \
  -E "Filters\.eq\(.*\+|new Document\(.*\+" . \
  >> /tmp/sql-injection.txt
```

**Check 2: Command Injection**
```bash
# Find Runtime.exec with user input
grep -rn --include="*.java" \
  -E "Runtime\.getRuntime\(\)\.exec|ProcessBuilder.*\(" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  linenum=$(echo "$line" | cut -d: -f2)

  # Check if input is sanitized
  if ! grep -A10 -B10 "^$linenum" "$file" | grep -q "validate\|sanitize\|escape"; then
    echo "CRITICAL: $file:$linenum - Possible command injection"
  fi
done
```

**Check 3: LDAP Injection**
```bash
grep -rn --include="*.java" \
  -E "LdapContext|DirContext.*search" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if grep -q "String.*+\|concat" "$file"; then
    echo "HIGH: $file - Possible LDAP injection"
  fi
done
```

**Check 4: XML Injection (XXE)**
```bash
# Find XML parsers without secure configuration
grep -rn --include="*.java" \
  -E "DocumentBuilderFactory|SAXParserFactory|XMLInputFactory" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)

  # Check for secure defaults
  if ! grep -q "setFeature.*FEATURE_SECURE_PROCESSING\|disallow-doctype-decl" "$file"; then
    echo "HIGH: $file - XML parser may be vulnerable to XXE"
  fi
done
```

### A04:2021 - Insecure Design

**Check 1: Missing Rate Limiting**
```bash
# Check if endpoints have rate limiting
if ! grep -rq "@RateLimited\|RateLimiter\|Bucket4j" --include="*.java" .; then
  echo "MEDIUM: No rate limiting detected on API endpoints"
fi
```

**Check 2: Unrestricted Resource Consumption**
```bash
# Find file upload without size limits
grep -rn "@Consumes.*multipart" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "maxFileSize\|ContentLength\|size.*limit" "$file"; then
    echo "MEDIUM: $file - File upload without size restriction"
  fi
done
```

### A05:2021 - Security Misconfiguration

**Check 1: Exposed Stack Traces**
```bash
grep -rn "printStackTrace\|e\.getMessage" --include="*.java" . | \
  grep -E "Resource\.java|ExceptionMapper" | \
  while read line; do
    echo "MEDIUM: $line - Stack trace may leak in HTTP response"
  done
```

**Check 2: Verbose Error Messages**
```bash
# Check exception mappers
find . -name "*ExceptionMapper.java" | while read f; do
  if grep -q "ex\.getMessage\|ex\.toString" "$f"; then
    echo "MEDIUM: $f - Exception details exposed to client"
  fi
done
```

**Check 3: CORS Misconfiguration**
```bash
grep -rn "Access-Control-Allow-Origin.*\*" --include="*.java" --include="*.properties" . && \
  echo "HIGH: CORS allows all origins"
```

**Check 4: Debug Mode in Production**
```bash
grep -rn "debug\s*=\s*true\|quarkus\.log\.level\s*=\s*DEBUG" \
  --include="*.properties" --include="*.yaml" . | \
  grep -v "application-dev" && \
  echo "MEDIUM: Debug mode enabled in production config"
```

### A06:2021 - Vulnerable and Outdated Components

Covered in Step 2 (Dependency Security).

### A07:2021 - Identification and Authentication Failures

**Check 1: Weak JWT Configuration**
```bash
# Check JWT expiration
grep -rn "jwt.*expir\|token.*expir" --include="*.java" --include="*.properties" . | while read line; do
  if echo "$line" | grep -qE "[0-9]{5,}|365|999"; then
    echo "MEDIUM: $line - JWT expiration too long"
  fi
done

# Check JWT algorithm
grep -rn "Algorithm\." --include="*.java" . | while read line; do
  if echo "$line" | grep -q "Algorithm.none\|HMAC.*none"; then
    echo "CRITICAL: $line - JWT with 'none' algorithm"
  fi
done
```

**Check 2: Missing Brute Force Protection**
```bash
# Check login endpoints
grep -rn "@POST.*login\|authenticate" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "RateLimiter\|LoginAttempt\|throttle" "$file"; then
    echo "HIGH: $file - Login endpoint without brute force protection"
  fi
done
```

**Check 3: Insecure Session Management**
```bash
# Check session fixation protection
grep -rn "HttpSession" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "invalidate\|changeSessionId" "$file"; then
    echo "MEDIUM: $file - Possible session fixation vulnerability"
  fi
done
```

### A08:2021 - Software and Data Integrity Failures

**Check 1: Unsigned Module JARs**
```bash
# Check if module loading validates signatures
grep -rn "loadModule\|ClassLoader" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "verifySignature\|checksum\|hash" "$file"; then
    echo "HIGH: $file - Module loading without integrity check"
  fi
done
```

**Check 2: Insecure Deserialization**
```bash
grep -rn "ObjectInputStream\|readObject\|XMLDecoder" --include="*.java" . && \
  echo "HIGH: Java deserialization detected (potential RCE)"

# Jackson deserialization
grep -rn "@JsonTypeInfo\|enableDefaultTyping" --include="*.java" . && \
  echo "HIGH: Jackson polymorphic deserialization (potential RCE)"
```

### A09:2021 - Security Logging and Monitoring Failures

**Check 1: Missing Security Event Logging**
```bash
# Check if auth failures are logged
grep -rn "authenticate\|login" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "log\.warn\|log\.error\|logger\." "$file"; then
    echo "MEDIUM: $file - Authentication without logging"
  fi
done
```

**Check 2: Logging Sensitive Data**
```bash
# Find logging of passwords/tokens
grep -rn "log.*password\|log.*token\|log.*secret" --include="*.java" . | \
  grep -v "password.*\*\*\*\|token.*\*\*\*" | \
  while read line; do
    echo "HIGH: $line - Sensitive data in logs"
  done
```

### A10:2021 - Server-Side Request Forgery (SSRF)

**Check 1: Unvalidated URL Fetching**
```bash
# Find HTTP clients with user-controlled URLs
grep -rn "HttpClient\|WebClient\|RestClient" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if grep -q "request.*\+" "$file" && ! grep -q "validateUrl\|isAllowed\|whitelist" "$file"; then
    echo "HIGH: $file - Possible SSRF (unvalidated URL)"
  fi
done
```

---

## Step 4: Lightning Engine-Specific Security

### Module System Security

**Check 1: ClassLoader Isolation**
```bash
# Verify module isolation
grep -rn "ContainerClassLoader" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if grep -q "getSystemClassLoader\|getParent.*null" "$file"; then
    echo "HIGH: $file - ClassLoader may bypass isolation"
  fi
done
```

**Check 2: Malicious Module Protection**
```bash
# Check for SecurityManager usage
if ! grep -rq "SecurityManager\|AccessController" --include="*.java" .; then
  echo "MEDIUM: No SecurityManager for module sandboxing"
fi

# Check module validation
grep -rn "install.*module\|loadModule" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "validate\|scan\|verify" "$file"; then
    echo "HIGH: $file - Module installation without security scan"
  fi
done
```

### Command Queue Security

**Check 1: Command Validation**
```bash
find . -name "*CommandHandler.java" -o -name "*Command.java" | while read f; do
  if ! grep -q "@Valid\|validate\|check" "$f"; then
    echo "HIGH: $f - Command handler missing input validation"
  fi
done
```

**Check 2: Command Origin Verification**
```bash
grep -rn "CommandQueue\|queueCommand" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "userId\|playerId\|sessionId" "$file"; then
    echo "MEDIUM: $file - Command without origin tracking"
  fi
done
```

### WebSocket Security

**Check 1: WebSocket Authentication**
```bash
find . -name "*WebSocket.java" | while read f; do
  if grep -q "@ServerEndpoint" "$f"; then
    if ! grep -q "@OnOpen.*authenticate\|validate.*session\|checkAuth" "$f"; then
      echo "CRITICAL: $f - WebSocket without authentication"
    fi
  fi
done
```

**Check 2: Message Validation**
```bash
grep -rn "@OnMessage" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -A10 "@OnMessage" "$file" | grep -q "validate\|sanitize"; then
    echo "HIGH: $file - WebSocket message without validation"
  fi
done
```

### Resource Upload Security

**Check 1: Path Traversal**
```bash
grep -rn "uploadResource\|saveFile\|writeFile" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "normalize\|canonicalize\|\\.\\." "$file"; then
    echo "CRITICAL: $file - File upload vulnerable to path traversal"
  fi
done
```

**Check 2: File Type Validation**
```bash
grep -rn "@Consumes.*multipart" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "contentType\|mime\|extension" "$file"; then
    echo "HIGH: $file - File upload without type validation"
  fi
done
```

---

## Step 5: Configuration Security

### Secrets Management

```bash
# Check for environment variable usage
if grep -rq "System\.getenv\|@ConfigProperty" --include="*.java" .; then
  echo "✓ Using environment variables for config"
else
  echo "MEDIUM: Secrets may be hardcoded"
fi

# Check .env files are gitignored
if [[ -f .gitignore ]] && ! grep -q "\.env" .gitignore; then
  echo "HIGH: .env not in .gitignore"
fi

# Find committed secrets
git log --all --full-history --source -- "*.env" "*.properties" | grep -q "password\|secret\|key" && \
  echo "CRITICAL: Secrets found in git history"
```

### Database Security

```bash
# Check MongoDB authentication
grep -rn "MongoClient" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if ! grep -q "credential\|username\|authentication" "$file"; then
    echo "HIGH: $file - MongoDB connection without authentication"
  fi
done

# Check for NoSQL injection prevention
grep -rn "Filters\.eq\|new Document" --include="*.java" . | while read line; do
  file=$(echo "$line" | cut -d: -f1)
  if grep -q "String.*+" "$file"; then
    echo "HIGH: $file - Possible NoSQL injection"
  fi
done
```

---

## Step 6: Generate Report

Create `security-review.json`:

```json
{
  "meta": {
    "analyzedAt": "2026-01-31T12:00:00Z",
    "path": ".",
    "mode": "full",
    "deepDive": true
  },
  "summary": {
    "critical": 2,
    "high": 8,
    "medium": 15,
    "low": 5,
    "info": 10
  },
  "byCategory": {
    "injection": {"critical": 1, "high": 2, "medium": 1},
    "auth": {"critical": 1, "high": 3, "medium": 2},
    "crypto": {"high": 1, "medium": 3},
    "config": {"medium": 4, "low": 2},
    "dependencies": {"high": 2, "medium": 5}
  },
  "findings": [
    {
      "id": "SEC-001",
      "severity": "critical",
      "category": "injection",
      "title": "Command Injection in ProcessExecutor",
      "file": "src/main/.../ProcessExecutor.java",
      "line": 45,
      "description": "Runtime.exec() called with unsanitized user input",
      "impact": "Remote code execution",
      "recommendation": "Use ProcessBuilder with argument array, validate input against whitelist",
      "cwe": "CWE-78",
      "owasp": "A03:2021"
    }
  ],
  "dependencies": {
    "total": 45,
    "vulnerable": 2,
    "vulnerabilities": [
      {
        "library": "com.fasterxml.jackson.core:jackson-databind",
        "version": "2.14.0",
        "cve": "CVE-2023-XXXXX",
        "severity": "high",
        "fixVersion": "2.14.2"
      }
    ]
  },
  "secrets": {
    "found": 3,
    "locations": [
      ".../application.properties:12 - JWT secret hardcoded"
    ]
  },
  "grade": "C",
  "securityScore": 68,
  "verdict": {
    "canDeploy": false,
    "blockers": [
      "2 critical vulnerabilities must be fixed",
      "3 hardcoded secrets must be removed"
    ]
  }
}
```

---

## Step 7: Present Summary

```markdown
## Security Review

**Security Score**: 68/100 | **Grade**: C

### Findings by Severity

| Severity | Count | Blocking |
|----------|-------|----------|
| CRITICAL | 2     | ✗ Yes    |
| HIGH     | 8     | ✗ Yes    |
| MEDIUM   | 15    | -        |
| LOW      | 5     | -        |

### By OWASP Category

| Category | Critical | High | Medium |
|----------|----------|------|--------|
| A01: Broken Access Control | 0 | 2 | 3 |
| A02: Cryptographic Failures | 1 | 1 | 4 |
| A03: Injection | 1 | 2 | 1 |
| A07: Auth Failures | 0 | 3 | 2 |

### Critical Issues (MUST FIX)

1. **[CRITICAL/Injection]** ProcessExecutor.java:45
   - Command injection via Runtime.exec()
   - Impact: Remote code execution
   - Fix: Use ProcessBuilder, validate input

2. **[CRITICAL/Auth]** SnapshotWebSocket.java:23
   - WebSocket endpoint without authentication
   - Impact: Unauthorized access to game state
   - Fix: Add JWT validation in @OnOpen

### High Priority Issues

3. **[HIGH/Injection]** MongoUserRepository.java:67
   - NoSQL injection in query builder
   - Fix: Use parameterized queries

4. **[HIGH/Secrets]** application.properties:12
   - Hardcoded JWT secret
   - Fix: Use environment variable

5. **[HIGH/Access Control]** ContainerResource.java:89
   - Missing authorization check on DELETE endpoint
   - Fix: Add @RolesAllowed("ADMIN")

### Dependency Vulnerabilities

| Library | Version | CVE | Severity |
|---------|---------|-----|----------|
| jackson-databind | 2.14.0 | CVE-2023-XXXXX | HIGH |
| netty-codec | 4.1.85 | CVE-2023-YYYYY | MEDIUM |

**Fix**: Run `mvn versions:use-latest-releases`

### Secrets Detected

- application.properties:12 - JWT secret
- AuthConfig.java:8 - API key
- .env.example:3 - Database password (example, safe)

**Fix**: Move to environment variables, add to .gitignore

### Lightning Engine-Specific

- ✓ ClassLoader isolation implemented
- ✗ Module JARs not signature-verified
- ✗ Command handlers missing input validation
- ✓ WebSocket has session tracking
- ✗ Resource upload vulnerable to path traversal

### Security Score Breakdown

| Area | Score | Weight |
|------|-------|--------|
| Injection Prevention | 60/100 | 20% |
| Authentication | 70/100 | 20% |
| Authorization | 65/100 | 15% |
| Crypto & Secrets | 55/100 | 15% |
| Input Validation | 75/100 | 15% |
| Dependencies | 80/100 | 10% |
| Logging | 85/100 | 5% |

**Overall**: 68/100 (C grade)

### Recommendations

**Immediate (Critical)**:
1. Fix command injection in ProcessExecutor
2. Add authentication to WebSocket endpoints
3. Remove hardcoded secrets, use env vars

**Short-term (High)**:
1. Add authorization checks to all DELETE endpoints
2. Implement rate limiting on login
3. Update vulnerable dependencies
4. Add module signature verification

**Medium-term**:
1. Implement comprehensive input validation framework
2. Add security event logging
3. Deploy SecurityManager for module sandboxing
4. Set up automated dependency scanning (Dependabot/Snyk)

### Verdict

⚠ **CANNOT DEPLOY** - 2 critical vulnerabilities must be fixed

- Command injection is exploitable
- Unauthenticated WebSocket exposes game state
- Hardcoded secrets compromise auth system

---
Report saved to `security-review.json`
```

---

## Grading

| Grade | Score | Criteria |
|-------|-------|----------|
| A | 90-100 | No critical/high, ≤5 medium, deps current |
| B | 80-89 | No critical, ≤2 high, ≤10 medium |
| C | 70-79 | ≤2 critical, ≤5 high |
| D | 60-69 | ≤5 critical, multiple high |
| F | <60 | >5 critical OR severe auth bypass |

**Can Deploy** criteria:
- No critical vulnerabilities
- No high-severity auth/injection issues
- No hardcoded secrets in production code
- Dependencies patched to non-vulnerable versions

---

## Project-Specific Security Guide

Based on Lightning Engine architecture, additional checks:

### ECS Security
- Component access must validate entity ownership
- Snapshot streaming must verify session permissions
- System execution must sanitize component data

### Multi-tenancy
- Container isolation must be enforced at ClassLoader level
- Match data must be scoped to authorized players only
- Commands must validate origin container/match

### Module Hot-Loading
- JAR signature verification before loading
- Module permissions manifest (what APIs can access)
- Sandbox module ClassLoader (restrict reflection, file I/O)

### AI Integration
- API keys must be per-container, not global
- AI responses must be sanitized before use in game logic
- Rate limit AI calls per match/user

---

## Integration with full-review

This skill is automatically invoked by `full-review` and contributes:
- Security findings to unified report
- Security grade to overall grade (20% weight)
- Blocking critical vulnerabilities to deployment verdict
