==========================
Tutorial: Hello StormStack
==========================

In this tutorial, you'll explore StormStack's core features by interacting with a running game match.

Time Required: 15 minutes

Prerequisites
-------------

* Completed :doc:`/getting-started/quickstart`
* StormStack services running
* CLI configured and authenticated

What You'll Learn
-----------------

* How Execution Containers work
* The tick cycle (commands, systems, snapshots)
* Spawning and manipulating entities
* Observing game state changes

Part 1: Understanding Containers
--------------------------------

Execution Containers are StormStack's isolated runtime environments. Each container has:

* Its own **ClassLoader** (module isolation)
* Its own **ECS store** (entity/component data)
* Its own **game loop** (independent tick rate)
* Its own **command queue**

Let's create a container and explore it.

Create a Container
~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Deploy creates a container and match for us
   lightning deploy --modules EntityModule,RigidBodyModule

   # Note the Match ID from the output (e.g., node-1-1-1)

Set Context
~~~~~~~~~~~

.. code-block:: bash

   # Set context so subsequent commands target this match
   lightning node context match node-1-1-1

List Containers
~~~~~~~~~~~~~~~

.. code-block:: bash

   # View all containers on the node
   lightning node context set node-1
   # Then use the REST API or web dashboard to inspect

Part 2: The Tick Cycle
----------------------

StormStack uses a tick-based simulation. Each tick:

1. **Commands execute** - Queued commands run
2. **Systems run** - Module systems update state
3. **Snapshot generated** - Current state captured
4. **Broadcast** - Snapshot sent via WebSocket

Observe the Tick
~~~~~~~~~~~~~~~~

Get the current tick number:

.. code-block:: bash

   lightning snapshot get -o json | grep tick

Advance Manually
~~~~~~~~~~~~~~~~

Let's advance the simulation one tick at a time:

.. code-block:: bash

   # Advance one tick
   lightning node tick advance

   # Check the tick number again
   lightning snapshot get -o json | grep tick

The tick number should have increased by 1.

Part 3: Spawning Entities
-------------------------

Now let's create some entities and watch them appear in the snapshot.

Spawn an Entity
~~~~~~~~~~~~~~~

.. code-block:: bash

   # Spawn a player entity (type 100)
   lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'

   # Advance a tick to process the command
   lightning node tick advance

View the Entity
~~~~~~~~~~~~~~~

.. code-block:: bash

   lightning snapshot get -o json

You should see your entity in the output:

.. code-block:: json

   {
     "matchId": 1,
     "tick": 2,
     "modules": [
       {
         "name": "EntityModule",
         "version": "1.0",
         "components": [
           {
             "name": "ENTITY_ID",
             "values": [1]
           },
           {
             "name": "ENTITY_TYPE",
             "values": [100]
           }
         ]
       }
     ]
   }

Spawn More Entities
~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Spawn several more entities
   lightning command send spawn '{"matchId":1,"playerId":1,"entityType":200}'
   lightning command send spawn '{"matchId":1,"playerId":2,"entityType":100}'
   lightning command send spawn '{"matchId":1,"playerId":2,"entityType":300}'

   # Process them
   lightning node tick advance

   # View all entities
   lightning snapshot get -o json

Part 4: Real-Time Simulation
----------------------------

Instead of advancing manually, let's run the simulation in real-time.

Start Auto-Advance
~~~~~~~~~~~~~~~~~~

.. note::

   Before running simulation commands, ensure you have set both node and match context:

   .. code-block:: bash

      lightning node context set node-1
      lightning node context match node-1-1-1

.. code-block:: bash

   # Run at 60 FPS (16ms per tick)
   lightning node simulation play --interval-ms 16

Watch State Change
~~~~~~~~~~~~~~~~~~

With the simulation running, get snapshots to see the tick advancing:

.. code-block:: bash

   # Get snapshot (tick will be higher now)
   lightning snapshot get -o json | grep tick

   # Wait a second and check again
   sleep 1
   lightning snapshot get -o json | grep tick

Stop the Simulation
~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   lightning node simulation stop

Part 5: WebSocket Streaming
---------------------------

For real game clients, you'd connect via WebSocket to receive snapshots in real-time.

The WebSocket endpoint is:

.. code-block:: text

   ws://localhost:8080/ws/containers/{containerId}/matches/{matchId}/snapshot

You can test with the CLI:

.. code-block:: bash

   # Stream snapshots to terminal
   lightning ws connect snapshot

Part 6: Cleanup
---------------

When you're done experimenting:

.. code-block:: bash

   # Delete the match (optional)
   lightning match delete node-1-1-1

Key Concepts Reviewed
---------------------

* **Execution Container**: Isolated runtime with its own ECS, modules, and game loop
* **Tick Cycle**: Commands -> Systems -> Snapshots -> Broadcast
* **Entities**: Objects in the game world, identified by ID
* **Components**: Data attached to entities (ENTITY_ID, ENTITY_TYPE, etc.)
* **Commands**: Actions that modify game state (spawn, move, damage, etc.)
* **Snapshots**: Serialized ECS state at a point in time

Next Steps
----------

Ready to create your own module? Continue to :doc:`first-module`.

Want to learn more about these concepts?

* :doc:`/concepts/containers` - Deep dive into container isolation
* :doc:`/concepts/tick-cycle` - The tick lifecycle in detail
* :doc:`/concepts/ecs-basics` - Entity Component System fundamentals
