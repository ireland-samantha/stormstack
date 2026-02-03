==========
Code Style
==========

.. note::

   This page is under development.

This document describes the code style conventions for StormStack.

Java Code Style
---------------

- Java 25 with preview features enabled
- Follow standard Java naming conventions
- Use ``@Override`` annotations
- Prefer records for DTOs

Core Module Rules
~~~~~~~~~~~~~~~~~

- **NO** framework annotations (``@Inject``, ``@ApplicationScoped``, etc.)
- Framework-agnostic implementations
- Pure domain logic only

Provider Module Rules
~~~~~~~~~~~~~~~~~~~~~

- Quarkus annotations allowed
- ``ServiceProducer`` pattern for DI
- REST resources in ``rest/`` package

Go Code Style
-------------

- Go 1.24+
- Standard ``gofmt`` formatting
- Cobra for CLI commands

TypeScript/React
----------------

- React 18+ with TypeScript
- Material-UI components
- Functional components with hooks

See :doc:`/contributing/index` for the full contributing guide.
