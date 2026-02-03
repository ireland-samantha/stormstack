============
Contributing
============

Thank you for your interest in contributing to StormStack!

This guide covers how to set up your development environment and contribute to the project.

Getting Started
---------------

1. Fork the repository on GitHub
2. Clone your fork:

   .. code-block:: bash

      git clone https://github.com/YOUR-USERNAME/lightning-engine.git
      cd lightning-engine

3. Set up the development environment (see below)

Development Environment
-----------------------

Prerequisites
~~~~~~~~~~~~~

* **Java 25** with preview features enabled
* **Maven 3.9+**
* **Go 1.24+** (for CLI development)
* **Node.js 18+** and npm (for frontend)
* **Docker** and Docker Compose
* **MongoDB 6.0+** (or use Docker)

IDE Setup
~~~~~~~~~

**IntelliJ IDEA** (Recommended):

1. Open the root ``pom.xml`` as a project
2. Enable annotation processing (Settings > Build > Compiler > Annotation Processors)
3. Set Project SDK to Java 25
4. Enable preview features in compiler settings

**VS Code**:

1. Install "Extension Pack for Java"
2. Open the project folder
3. Extensions will auto-detect the Maven project

Build Commands
~~~~~~~~~~~~~~

.. code-block:: bash

   ./build.sh clean            # Clean build artifacts
   ./build.sh build            # Build all modules (skip tests)
   ./build.sh test             # Run unit tests
   ./build.sh docker           # Build Docker images
   ./build.sh integration-test # Build + run integration tests
   ./build.sh all              # Full pipeline

Or use Maven directly:

.. code-block:: bash

   mvn clean install -DskipTests  # Quick build
   mvn clean install              # Build with tests

Project Structure
-----------------

.. code-block:: text

   stormstack/
   +-- thunder/                 # Backend services (Java)
   |   +-- auth/               # Authentication service
   |   +-- control-plane/      # Cluster management
   |   +-- engine/             # Game engine
   |   |   +-- core/          # Domain + implementation
   |   |   +-- provider/      # Quarkus REST/WebSocket
   |   |   +-- adapters/      # SDKs and clients
   |   +-- shared/            # Shared utilities
   +-- lightning/              # Client tools
   |   +-- cli/               # Go CLI
   |   +-- webpanel/          # React admin panel
   |   +-- rendering/         # Java GUI framework
   +-- crates/                 # Rust rewrite (in progress)
   +-- docs/                   # Documentation

Code Style
----------

Java
~~~~

* Follow Google Java Style Guide
* Use meaningful names (no abbreviations unless standard)
* Keep methods under 30 lines when possible
* Write Javadoc for all public APIs

Naming conventions:

* Domain model: ``Match``, ``ExecutionContainer``
* Strongly-typed ID: ``MatchId``, ``ContainerId``
* Service interface: ``MatchService``
* Service implementation: ``MatchServiceImpl``
* Repository interface: ``MatchRepository``
* MongoDB implementation: ``MongoMatchRepository``
* DTOs: ``CreateMatchRequest``, ``MatchResponse``
* REST Resources: ``MatchResource``
* Exceptions: ``MatchNotFoundException``

Go (CLI)
~~~~~~~~

* Run ``go fmt`` before committing
* Use ``golint`` and ``go vet``
* Follow standard Go project layout

TypeScript (Frontend)
~~~~~~~~~~~~~~~~~~~~~

* Use ESLint and Prettier
* Follow React best practices
* Prefer functional components with hooks

Architecture Principles
-----------------------

1. **Separation of Concerns**: Each module has one clear responsibility
2. **Single Responsibility**: A module should have only one reason to change
3. **Dependency Injection**: All dependencies are injected, not instantiated
4. **Depend on Abstractions**: Use interfaces, not implementations
5. **Two-Module Pattern**: Separate core (domain) from provider (framework)

For the Two-Module Pattern:

* **Core Module** (``*/core/``): Interfaces, implementations, domain models - NO framework annotations
* **Provider Module** (``*/provider/``): Quarkus-specific code, REST endpoints, persistence

.. warning::

   Service implementations in ``*-core`` modules must have **NO framework annotations**.
   No ``@Inject``, ``@ApplicationScoped``, ``@Singleton`` in core modules!

Submitting Changes
------------------

1. Create a feature branch:

   .. code-block:: bash

      git checkout -b feature/my-feature

2. Make your changes following code style guidelines

3. Write or update tests

4. Run the full test suite:

   .. code-block:: bash

      ./build.sh all

5. Commit with a conventional commit message:

   .. code-block:: text

      type(scope): subject

      body (optional)

      footer (optional)

   Types: ``feat``, ``fix``, ``docs``, ``style``, ``refactor``, ``test``, ``chore``

6. Push and create a Pull Request

Quality Checklist
-----------------

Before submitting a PR, verify:

* [ ] Interface has complete Javadoc
* [ ] DTOs use Java records with validation annotations
* [ ] Custom exceptions for all failure cases
* [ ] All layers implemented (resource > service > repository)
* [ ] **Service implementations in core modules have NO framework annotations**
* [ ] Unit tests for all classes (>80% coverage)
* [ ] Integration tests with Testcontainers (MongoDB)
* [ ] ``./build.sh all`` passes
* [ ] No security vulnerabilities

Testing
-------

Unit Tests
~~~~~~~~~~

.. code-block:: bash

   mvn test

Integration Tests
~~~~~~~~~~~~~~~~~

Requires Docker:

.. code-block:: bash

   mvn verify -Pacceptance-tests

E2E Tests
~~~~~~~~~

.. code-block:: bash

   ./build.sh e2e-test

Getting Help
------------

* Open an issue for bugs or feature requests
* Check existing issues before creating new ones
* For questions, use GitHub Discussions

License
-------

By contributing, you agree that your contributions will be licensed under the MIT License.
