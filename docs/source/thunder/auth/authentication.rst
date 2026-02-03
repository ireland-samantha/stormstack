===================
Authentication
===================

Thunder Auth implements OAuth2 (RFC 6749) for token-based authentication.
This page covers the authentication flows, JWT token structure, and client setup.

.. contents:: Contents
   :local:
   :depth: 2

OAuth2 Grant Types
==================

Thunder Auth supports four OAuth2 grant types:

Client Credentials
------------------

**Use case**: Service-to-service authentication (CLI, automated tools)

This is the recommended grant type for non-interactive clients.

Request::

    POST /oauth2/token
    Content-Type: application/x-www-form-urlencoded

    grant_type=client_credentials
    &client_id=my-service
    &client_secret=my-secret
    &scope=engine.container.create control-plane.match.read

Response::

    {
      "access_token": "eyJhbGciOiJSUzI1NiIs...",
      "token_type": "Bearer",
      "expires_in": 3600,
      "scope": "engine.container.create control-plane.match.read"
    }

**HTTP Basic Auth Alternative**::

    POST /oauth2/token
    Authorization: Basic base64(client_id:client_secret)
    Content-Type: application/x-www-form-urlencoded

    grant_type=client_credentials

Password Grant
--------------

**Use case**: User login from trusted first-party applications

.. warning::

    Only use this grant type for first-party applications where you control
    both the client and server. Third-party apps should use authorization code flow.

Request::

    POST /oauth2/token
    Content-Type: application/x-www-form-urlencoded

    grant_type=password
    &client_id=web-panel
    &username=admin
    &password=admin123
    &scope=*

Response includes refresh token::

    {
      "access_token": "eyJhbGciOiJSUzI1NiIs...",
      "token_type": "Bearer",
      "expires_in": 3600,
      "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2g...",
      "scope": "*"
    }

Refresh Token
-------------

**Use case**: Obtaining new access tokens without re-authentication

Request::

    POST /oauth2/token
    Content-Type: application/x-www-form-urlencoded

    grant_type=refresh_token
    &client_id=web-panel
    &refresh_token=dGhpcyBpcyBhIHJlZnJlc2g...

Response::

    {
      "access_token": "eyJhbGciOiJSUzI1NiIs...",
      "token_type": "Bearer",
      "expires_in": 3600,
      "refresh_token": "bmV3IHJlZnJlc2ggdG9rZW4...",
      "scope": "*"
    }

.. note::

    Refresh tokens are rotated on each use. The old refresh token becomes invalid.

Token Exchange (RFC 8693)
-------------------------

**Use case**: Exchanging API tokens for session tokens

Request::

    POST /oauth2/token
    Content-Type: application/x-www-form-urlencoded

    grant_type=urn:ietf:params:oauth:grant-type:token-exchange
    &subject_token=api_xxxxxxxxxxxxx
    &subject_token_type=urn:stormstack:token-type:api-token

Response::

    {
      "access_token": "eyJhbGciOiJSUzI1NiIs...",
      "token_type": "Bearer",
      "expires_in": 3600,
      "issued_token_type": "urn:ietf:params:oauth:token-type:access_token"
    }

JWT Token Structure
===================

Access tokens are JSON Web Tokens (JWT) with the following structure:

Header
------

::

    {
      "alg": "RS256",
      "typ": "JWT"
    }

Thunder Auth supports:

- **RS256** (RSA-SHA256) - Recommended for production
- **HS256** (HMAC-SHA256) - Fallback when RSA keys not configured

Payload (Claims)
----------------

::

    {
      "iss": "https://auth.stormstack.io",
      "sub": "admin",
      "upn": "admin",
      "user_id": "usr_abc123",
      "username": "admin",
      "roles": ["admin", "operator"],
      "scopes": ["*"],
      "iat": 1706889600,
      "exp": 1706893200
    }

.. list-table:: JWT Claims
   :widths: 20 80
   :header-rows: 1

   * - Claim
     - Description
   * - ``iss``
     - Token issuer (configured via ``jwt-issuer``)
   * - ``sub``
     - Subject (username)
   * - ``upn``
     - MicroProfile JWT user principal name
   * - ``user_id``
     - StormStack user ID
   * - ``username``
     - User's username
   * - ``roles``
     - Array of role names
   * - ``scopes``
     - Array of permission scopes
   * - ``iat``
     - Issued at timestamp (Unix epoch)
   * - ``exp``
     - Expiration timestamp (Unix epoch)

Token Validation
================

Tokens are validated without database lookup using:

1. **Signature verification** - Using configured public key or secret
2. **Expiration check** - Token must not be expired
3. **Issuer validation** - Must match configured issuer

Validation Endpoint
-------------------

For services that cannot validate JWT locally::

    POST /api/tokens/validate
    Content-Type: application/json

    {
      "token": "eyJhbGciOiJSUzI1NiIs..."
    }

Response (valid token)::

    {
      "valid": true,
      "user_id": "usr_abc123",
      "username": "admin",
      "scopes": ["*"],
      "expires_at": "2024-02-03T12:00:00Z"
    }

Response (invalid token)::

    {
      "valid": false,
      "error": "token_expired"
    }

Client Configuration
====================

Configuring Service Clients
---------------------------

Service clients are configured in ``application.yaml``::

    stormstack:
      oauth2:
        clients:
          lightning-cli:
            secret: "${AUTH_CLI_SECRET:dev-secret}"
            type: confidential
            scopes: ["*"]

          game-server:
            secret: "${AUTH_GAME_SECRET}"
            type: confidential
            scopes:
              - "engine.container.*"
              - "control-plane.match.*"

          public-client:
            type: public
            scopes:
              - "engine.container.read"
              - "control-plane.match.read"

Client Types
------------

**Confidential Clients**
    Have a secret and can authenticate to the token endpoint.
    Examples: Server-side applications, CLI tools.

**Public Clients**
    Cannot securely store a secret.
    Examples: Single-page apps, mobile apps.

Security Features
=================

Rate Limiting
-------------

The token endpoint includes built-in rate limiting to prevent brute force attacks:

- **Per-client limiting**: Based on IP + client_id/username
- **Exponential backoff**: Failed attempts increase lockout time
- **Retry-After header**: Indicates when to retry

When rate limited::

    HTTP/1.1 429 Too Many Requests
    Retry-After: 60
    X-RateLimit-Remaining: 0

    {
      "error": "rate_limit_exceeded",
      "error_description": "Too many requests. Please try again in 60 seconds."
    }

Brute Force Protection
----------------------

After multiple failed authentication attempts:

1. Account is not locked (to prevent denial of service)
2. Response time is artificially delayed
3. Rate limiting kicks in
4. All failures are logged for monitoring

HTTPS Enforcement
-----------------

In production, configure TLS at the load balancer or enable Quarkus HTTPS::

    quarkus:
      http:
        ssl:
          certificate:
            files: /etc/ssl/cert.pem
            key-files: /etc/ssl/key.pem

Error Handling
==============

OAuth2 Error Responses
----------------------

Errors follow RFC 6749 Section 5.2::

    HTTP/1.1 400 Bad Request
    Content-Type: application/json

    {
      "error": "invalid_grant",
      "error_description": "Invalid username or password"
    }

Common error codes:

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Error Code
     - Description
   * - ``invalid_request``
     - Missing or invalid parameter
   * - ``invalid_client``
     - Client authentication failed
   * - ``invalid_grant``
     - Invalid credentials or refresh token
   * - ``unauthorized_client``
     - Client not authorized for this grant type
   * - ``unsupported_grant_type``
     - Grant type not supported
   * - ``invalid_scope``
     - Requested scope is invalid

Next Steps
==========

- :doc:`authorization` - Learn about scopes and permissions
- :doc:`/api/rest-api` - Full API reference
- :doc:`/lightning/cli/commands` - CLI authentication commands
