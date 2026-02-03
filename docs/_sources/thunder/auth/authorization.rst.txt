================
Authorization
================

Thunder Auth uses scope-based authorization to control access to API endpoints.
This page covers the permission model, scope definitions, and how to secure endpoints.

.. contents:: Contents
   :local:
   :depth: 2

Permission Model
================

Thunder Auth uses a three-tier permission model:

1. **Users** - Human or service accounts
2. **Roles** - Named permission groups (admin, operator, viewer)
3. **Scopes** - Granular permissions (engine.container.create)

Users are assigned roles, and roles grant scopes. When a token is issued,
all scopes from the user's roles are resolved and included in the JWT.

.. code-block:: text

    User "alice"
        |
        +-- Role "operator"
        |       |
        |       +-- Scope "engine.container.*"
        |       +-- Scope "control-plane.match.*"
        |
        +-- Role "viewer"
                |
                +-- Scope "*.read"

Role Hierarchy
--------------

Roles can inherit from other roles::

    admin (inherits: operator)
        +-- Scope "auth.user.*"
        +-- Scope "auth.role.*"

    operator (inherits: viewer)
        +-- Scope "engine.container.*"
        +-- Scope "control-plane.*"

    viewer
        +-- Scope "engine.container.read"
        +-- Scope "control-plane.cluster.read"
        +-- Scope "control-plane.match.read"

Scope Syntax
============

Scopes follow a hierarchical naming convention::

    <service>.<resource>.<action>

Examples:

- ``engine.container.create`` - Create containers
- ``control-plane.match.read`` - Read match information
- ``auth.user.delete`` - Delete users

Wildcards
---------

Wildcards grant multiple permissions:

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Pattern
     - Grants
   * - ``*``
     - All permissions (superuser)
   * - ``engine.*``
     - All engine permissions
   * - ``control-plane.match.*``
     - All match operations
   * - ``*.read``
     - All read operations across services

Wildcard matching is prefix-based:

- ``control-plane.*`` matches ``control-plane.match.create``
- ``control-plane.match.*`` matches ``control-plane.match.create``
- ``control-plane.match.*`` does NOT match ``control-plane.node.register``

Scope Reference
===============

Engine Scopes
-------------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Scope
     - Description
   * - ``engine.container.create``
     - Create execution containers
   * - ``engine.container.read``
     - View container details and list containers
   * - ``engine.container.delete``
     - Delete containers
   * - ``engine.match.create``
     - Create matches within containers
   * - ``engine.match.read``
     - View match details and snapshots
   * - ``engine.match.update``
     - Modify match state (join players, etc.)
   * - ``engine.match.delete``
     - Delete/finish matches
   * - ``engine.module.install``
     - Install modules to containers
   * - ``engine.module.read``
     - View installed modules
   * - ``engine.command.send``
     - Send commands to matches
   * - ``engine.snapshot.read``
     - Read ECS snapshots
   * - ``engine.session.create``
     - Create player sessions
   * - ``engine.session.read``
     - View player sessions

Control Plane Scopes
--------------------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Scope
     - Description
   * - ``control-plane.cluster.read``
     - View cluster status and node list
   * - ``control-plane.node.register``
     - Register nodes and send heartbeats
   * - ``control-plane.node.manage``
     - Drain and deregister nodes
   * - ``control-plane.node.proxy``
     - Proxy requests to nodes
   * - ``control-plane.match.create``
     - Create matches via control plane
   * - ``control-plane.match.read``
     - View match registry
   * - ``control-plane.match.update``
     - Update match player count
   * - ``control-plane.match.delete``
     - Delete matches from registry
   * - ``control-plane.module.upload``
     - Upload modules to registry
   * - ``control-plane.module.read``
     - View and download modules
   * - ``control-plane.module.delete``
     - Delete modules
   * - ``control-plane.module.distribute``
     - Distribute modules to nodes
   * - ``control-plane.deploy.create``
     - Deploy matches to cluster
   * - ``control-plane.deploy.read``
     - View deployment status
   * - ``control-plane.deploy.delete``
     - Undeploy matches
   * - ``control-plane.autoscaler.read``
     - View autoscaler recommendations
   * - ``control-plane.autoscaler.manage``
     - Configure and acknowledge scaling
   * - ``control-plane.dashboard.read``
     - View dashboard data

Auth Scopes
-----------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Scope
     - Description
   * - ``auth.user.create``
     - Create user accounts
   * - ``auth.user.read``
     - View user details
   * - ``auth.user.update``
     - Modify user accounts
   * - ``auth.user.delete``
     - Delete users
   * - ``auth.role.create``
     - Create roles
   * - ``auth.role.read``
     - View role details
   * - ``auth.role.update``
     - Modify roles
   * - ``auth.role.delete``
     - Delete roles
   * - ``auth.token.create``
     - Create API tokens
   * - ``auth.token.read``
     - View API tokens
   * - ``auth.token.delete``
     - Revoke API tokens

Securing Endpoints
==================

Using @Scopes Annotation
------------------------

REST endpoints are secured using the ``@Scopes`` annotation:

.. code-block:: java

    @Path("/api/containers")
    public class ContainerResource {

        @POST
        @Scopes("engine.container.create")
        public Response create(ContainerRequest request) {
            // Only accessible with engine.container.create scope
        }

        @GET
        @Scopes("engine.container.read")
        public List<ContainerResponse> list() {
            // Only accessible with engine.container.read scope
        }

        @DELETE
        @Path("/{id}")
        @Scopes("engine.container.delete")
        public Response delete(@PathParam("id") String id) {
            // Only accessible with engine.container.delete scope
        }
    }

Multiple Scopes (OR)
--------------------

Require any of multiple scopes::

    @Scopes({"engine.container.read", "control-plane.cluster.read"})
    public Response getStatus() {
        // Accessible with either scope
    }

Class-Level Scopes
------------------

Apply to all methods in a class::

    @Path("/api/admin")
    @Scopes("auth.user.*")
    public class AdminResource {
        // All methods require auth.user.* scope
    }

Checking Scopes Programmatically
--------------------------------

Access the security context in your code:

.. code-block:: java

    @Context
    SecurityContext securityContext;

    public void someMethod() {
        LightningPrincipal principal = (LightningPrincipal)
            securityContext.getUserPrincipal();

        if (principal.hasScope("engine.container.delete")) {
            // User can delete containers
        }

        Set<String> scopes = principal.getScopes();
        // ["engine.container.create", "engine.container.read", ...]
    }

Role-Based Patterns
===================

Common Role Configurations
--------------------------

**Administrator**
::

    {
      "name": "admin",
      "scopes": ["*"],
      "inherits": []
    }

**Operator**
::

    {
      "name": "operator",
      "scopes": [
        "engine.*",
        "control-plane.*"
      ],
      "inherits": ["viewer"]
    }

**Module Developer**
::

    {
      "name": "module-developer",
      "scopes": [
        "control-plane.module.*",
        "engine.module.install",
        "engine.container.*"
      ],
      "inherits": []
    }

**Read-Only Viewer**
::

    {
      "name": "viewer",
      "scopes": [
        "engine.container.read",
        "engine.match.read",
        "engine.snapshot.read",
        "control-plane.cluster.read",
        "control-plane.match.read",
        "control-plane.deploy.read",
        "control-plane.dashboard.read"
      ],
      "inherits": []
    }

**Node Agent** (for engine nodes registering with control plane)
::

    {
      "name": "node-agent",
      "scopes": [
        "control-plane.node.register"
      ],
      "inherits": []
    }

**Game Client** (minimal permissions for players)
::

    {
      "name": "game-client",
      "scopes": [
        "engine.match.read",
        "engine.command.send",
        "engine.snapshot.read"
      ],
      "inherits": []
    }

Managing Roles via API
======================

Create a Role
-------------

::

    POST /api/roles
    Authorization: Bearer <token>
    Content-Type: application/json

    {
      "name": "custom-operator",
      "description": "Custom operator role",
      "scopes": [
        "engine.container.*",
        "control-plane.match.*"
      ],
      "inherits": ["viewer"]
    }

Assign Role to User
-------------------

::

    PUT /api/users/{userId}/roles
    Authorization: Bearer <token>
    Content-Type: application/json

    {
      "roles": ["operator", "module-developer"]
    }

Security Best Practices
=======================

1. **Principle of Least Privilege** - Grant minimum scopes necessary
2. **Use Specific Scopes** - Avoid wildcards in production clients
3. **Separate Service Accounts** - Each service should have its own client
4. **Audit Scope Usage** - Log which scopes are used for compliance
5. **Regular Review** - Periodically review role assignments

Common Mistakes
---------------

- Granting ``*`` to non-admin accounts
- Using the same client credentials across environments
- Not revoking access when team members leave
- Forgetting to add scopes to new endpoints

Troubleshooting
===============

403 Forbidden Errors
--------------------

If you receive a 403 error:

1. Check the token has the required scope::

    # Decode JWT to see scopes
    echo $TOKEN | cut -d. -f2 | base64 -d | jq .scopes

2. Verify the endpoint's ``@Scopes`` annotation
3. Check for wildcard scope matching issues
4. Ensure the user's roles include the needed scope

Missing Scopes in Token
-----------------------

If expected scopes are missing from the token:

1. Check the user's role assignments
2. Verify role inheritance is configured correctly
3. Check if the client was configured with scope restrictions
4. Ensure scope names match exactly (case-sensitive)

Next Steps
==========

- :doc:`authentication` - Authentication flows and token management
- :doc:`/api/rest-api` - Full API reference with scope requirements
- :doc:`/contributing/code-style` - How to add scopes to new endpoints
