=======
Modules
=======

.. note::

   This page is under development.

Modules are hot-reloadable JAR files that define game logic.

What Modules Provide
--------------------

Each module can contribute:

**Components**
   Data structures attached to entities (e.g., Position, Health)

**Systems**
   Logic that runs each tick (e.g., MovementSystem, DamageSystem)

**Commands**
   Actions that can be triggered via the API (e.g., SpawnEntity, Attack)

Module Lifecycle
----------------

1. **Create** - Build a JAR implementing ``EngineModule``
2. **Install** - Upload to a container via ``lightning module install``
3. **Initialize** - Module registers its components, systems, and commands
4. **Run** - Systems execute each tick
5. **Uninstall** - Module can be removed at runtime

See :doc:`/tutorials/first-module` for a complete walkthrough.
