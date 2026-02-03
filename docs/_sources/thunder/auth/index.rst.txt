====================
Thunder Auth Service
====================

Thunder Auth provides authentication and authorization for all StormStack services.
It implements OAuth2 (RFC 6749) with JWT tokens and role-based access control.

.. contents:: Contents
   :local:
   :depth: 2

Overview
========

Thunder Auth is a standalone authentication service that issues and validates JWT tokens
for all Thunder services. It provides:

- **OAuth2 Token Endpoint** - Standard-compliant token issuance
- **JWT Authentication** - Stateless token validation with RSA256 or HMAC256
- **Role-Based Access Control** - Hierarchical roles with inherited permissions
- **Scope-Based Authorization** - Fine-grained permissions for API endpoints
- **Security Features** - Rate limiting, brute force protection, token refresh

Architecture
------------

Thunder Auth follows the two-module pattern:

.. code-block:: text

    thunder/auth/
        core/           # Framework-agnostic domain logic
            model/      # User, Role, Token domain models
            service/    # AuthenticationService, OAuth2TokenService
            repository/ # UserRepository, RoleRepository interfaces
        provider/       # Quarkus application
            http/       # REST endpoints (TokenEndpoint, UserResource)
            persistence/ # MongoDB implementations
            config/     # Quarkus configuration bindings
        adapters/       # Framework adapters
            quarkus/    # Quarkus security integration
            spring/     # Spring Boot adapter

The core module has **zero framework dependencies** - all Quarkus annotations are
isolated in the provider module.

Quick Start
===========

For Local Development
---------------------

A default service client is configured for development::

    # Get an access token
    curl -X POST http://localhost:8080/oauth2/token \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "grant_type=client_credentials" \
      -d "client_id=lightning-cli" \
      -d "client_secret=dev-secret"

Response::

    {
      "access_token": "eyJhbGciOiJSUzI1NiIs...",
      "token_type": "Bearer",
      "expires_in": 3600
    }

Using the Token
---------------

Include the token in API requests::

    curl -H "Authorization: Bearer <token>" \
      http://localhost:8080/api/containers

Using Lightning CLI
-------------------

The CLI handles authentication automatically::

    # Configure and login
    lightning config set auth.client_id lightning-cli
    lightning config set auth.client_secret dev-secret
    lightning auth login

Documentation
=============

.. toctree::
   :maxdepth: 2

   authentication
   authorization

Key Concepts
============

Authentication vs Authorization
-------------------------------

- **Authentication**: "Who are you?" - Verifying identity via credentials
- **Authorization**: "What can you do?" - Checking permissions via scopes

Thunder Auth handles both:

1. **Authentication** happens at the ``/oauth2/token`` endpoint
2. **Authorization** happens at each protected API endpoint via ``@Scopes``

Tokens
------

Thunder Auth uses two types of tokens:

**Access Tokens** (JWT)
    Short-lived tokens (default: 1 hour) for API access. Contain user identity
    and scopes. Validated without database lookup.

**Refresh Tokens**
    Long-lived tokens for obtaining new access tokens without re-authentication.
    Stored server-side and can be revoked.

Scopes
------

Scopes are permission strings that control API access. Examples:

- ``engine.container.create`` - Create execution containers
- ``control-plane.cluster.read`` - View cluster status
- ``auth.user.create`` - Create users

Scopes support wildcards: ``engine.*`` grants all engine permissions.

See :doc:`authorization` for the complete scope matrix.

Configuration
=============

Key configuration options (in ``application.yaml``)::

    stormstack:
      auth:
        jwt-issuer: "https://auth.stormstack.io"
        session-expiry-hours: 24
        bcrypt-cost: 12
        # RSA keys for production
        private-key-location: "classpath:keys/private.pem"
        public-key-location: "classpath:keys/public.pem"

      oauth2:
        access-token-ttl-seconds: 3600
        refresh-token-ttl-seconds: 86400
        clients:
          lightning-cli:
            secret: "${AUTH_CLI_SECRET}"
            scopes: ["*"]

Security Best Practices
=======================

1. **Use RSA256 in Production** - Configure RSA key pair instead of HMAC256
2. **Rotate Secrets** - Regularly rotate client secrets and refresh tokens
3. **Minimal Scopes** - Grant only necessary permissions to each client
4. **HTTPS Only** - Always use TLS in production
5. **Rate Limiting** - Built-in protection against brute force attacks

API Endpoints
=============

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``POST /oauth2/token``
     - OAuth2 token endpoint
   * - ``GET /.well-known/openid-configuration``
     - OpenID Connect discovery
   * - ``GET /.well-known/jwks.json``
     - JSON Web Key Set
   * - ``GET /api/users``
     - User management (admin)
   * - ``GET /api/roles``
     - Role management (admin)
   * - ``POST /api/tokens/validate``
     - Token validation endpoint

See :doc:`/api/rest-api` for complete API documentation.
