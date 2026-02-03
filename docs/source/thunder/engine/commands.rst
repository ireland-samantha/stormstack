===============
Command System
===============

.. note::

   This page is under development.

Commands are actions that modify game state, queued for execution within ticks.

How Commands Work
-----------------

1. **Queue** - Commands are submitted via REST API or CLI
2. **Validate** - Command parameters are validated against schema
3. **Execute** - Commands run at the start of the next tick
4. **Result** - Success/failure is reported

Built-in Commands
-----------------

Modules can provide commands. Common examples:

- ``SpawnEntity`` - Create a new entity
- ``DestroyEntity`` - Remove an entity
- ``SetComponent`` - Update component values

API Usage
---------

.. code-block:: bash

   # Queue a command
   lightning command send spawn '{"matchId":1,"entityType":100}'

   # Advance tick to execute
   lightning node simulation tick

See :doc:`/api/rest-api` for the REST API reference.
