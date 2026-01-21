# Security Policy

## Supported Versions

As this is a pre-alpha hobby project, only the latest version on the `main` branch receives security updates.

| Version | Supported          |
| ------- | ------------------ |
| main    | :white_check_mark: |
| others  | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in Lightning Engine, please report it responsibly:

1. **Do not** open a public GitHub issue for security vulnerabilities
2. Instead, please open a [private security advisory](../../security/advisories/new) on GitHub
3. Include as much detail as possible:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## What to Expect

- **Acknowledgment**: We will acknowledge receipt of your report within 48 hours
- **Updates**: We will keep you informed of our progress
- **Resolution**: Once fixed, we will credit you in the release notes (unless you prefer to remain anonymous)

## Security Best Practices for Users

When deploying Lightning Engine:

1. **Environment Variables**: Never commit secrets to version control. Use environment variables for:
   - `ADMIN_INITIAL_PASSWORD`
   - `CORS_ORIGINS`
   - Database credentials

2. **CORS Configuration**: In production, always set `CORS_ORIGINS` to your specific domain(s)

3. **JWT Security**: The application generates unique JWT keys at build time. Never share or expose these keys

4. **Network Security**: Run behind a reverse proxy (nginx, Traefik) with TLS in production

5. **MongoDB**: Secure your MongoDB instance with authentication and network restrictions

## Scope

This security policy applies to the Lightning Engine codebase. Third-party dependencies are subject to their own security policies.
