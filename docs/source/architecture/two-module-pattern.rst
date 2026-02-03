==================
Two-Module Pattern
==================

.. note::

   This page is under development.

StormStack uses a consistent two-module pattern for service organization.

The Pattern
-----------

Each service domain has two Maven modules:

**Core Module** (``*/core/``)
   - Interfaces and domain models
   - Service implementations
   - NO framework annotations
   - Framework-agnostic

**Provider Module** (``*/provider/``)
   - Quarkus REST endpoints
   - Framework configuration
   - ``ServiceProducer`` classes for DI
   - MongoDB persistence

Benefits
--------

1. **Testability** - Core logic can be unit tested without framework
2. **Portability** - Core can be used with different frameworks
3. **Clean boundaries** - Clear separation of concerns

Example
-------

.. code-block:: text

   thunder/auth/
   ├── core/           # MatchService, MatchServiceImpl, MatchRepository
   └── provider/       # MatchResource, MongoMatchRepository, ServiceProducer
