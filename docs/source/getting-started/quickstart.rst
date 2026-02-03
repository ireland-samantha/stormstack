==========
Quickstart
==========

Deploy your first game match in 5 minutes!

This guide assumes you've completed :doc:`installation` and have services running.

Step 1: Configure the CLI
-------------------------

Tell the CLI where to find your control plane:

.. code-block:: bash

   lightning config set control_plane_url http://localhost:8081

Step 2: Authenticate
--------------------

Log in with your admin credentials:

.. code-block:: bash

   lightning auth login --username admin --password <your-password>

   # Verify you're authenticated
   lightning auth status

.. note::

   Replace ``<your-password>`` with the value you set for ``ADMIN_INITIAL_PASSWORD``.

Step 3: Check Cluster Health
----------------------------

Verify the cluster is ready:

.. code-block:: bash

   lightning cluster status

You should see output like:

.. code-block:: text

   Cluster Status:
     Total Nodes:     1
     Healthy Nodes:   1
     Draining Nodes:  0
     Total Capacity:  100
     Used Capacity:   0
     Saturation:      0.0%

Step 4: Deploy a Match
----------------------

Deploy a game match with the default modules:

.. code-block:: bash

   lightning deploy --modules EntityModule,RigidBodyModule,RenderingModule

You'll see output like:

.. code-block:: text

   Match deployed successfully!
     Match ID:     node-1-1-1
     Node:         node-1
     Container:    1
     Status:       RUNNING

   Endpoints:
     HTTP:         http://backend:8080/api/containers/1
     WebSocket:    ws://backend:8080/ws/containers/1/matches/1/snapshots
     Commands:     ws://backend:8080/ws/containers/1/matches/1/commands

Step 5: View the Match
----------------------

List your running matches:

.. code-block:: bash

   lightning match list

Step 6: Get Game State
----------------------

First, set the context to your match:

.. code-block:: bash

   lightning node context match node-1-1-1

Then retrieve the current game snapshot:

.. code-block:: bash

   lightning snapshot get

You'll see the ECS state in JSON format:

.. code-block:: json

   {
     "matchId": 1,
     "tick": 0,
     "modules": [
       {
         "name": "EntityModule",
         "version": "1.0",
         "components": []
       }
     ]
   }

Step 7: Start the Simulation
----------------------------

Start the game loop running at 60 FPS (16ms per tick):

.. code-block:: bash

   lightning node simulation play --interval-ms 16

Step 8: Send a Command
----------------------

Spawn an entity in the game:

.. code-block:: bash

   lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'

Step 9: See the Result
----------------------

Get the snapshot again to see your spawned entity:

.. code-block:: bash

   lightning snapshot get -o json

You've Done It!
---------------

Congratulations! You've:

1. Connected to a StormStack cluster
2. Deployed a game match with modules
3. Started the simulation
4. Sent commands and observed game state

What's Happening Behind the Scenes
----------------------------------

Here's what StormStack did for you:

1. **Created an Execution Container** - An isolated runtime with its own game loop, ECS store, and modules
2. **Loaded Modules** - EntityModule, RigidBodyModule, and RenderingModule provide components and systems
3. **Created a Match** - A game session within the container
4. **Started the Tick Loop** - Every 16ms, systems run and snapshots are generated

The tick cycle works like this:

.. code-block:: text

   Commands Queue -> Tick Advances -> Systems Run -> Snapshot Generated -> WebSocket Broadcast

For more details, see :doc:`/concepts/tick-cycle`.

Next Steps
----------

* :doc:`/tutorials/hello-stormstack` - A deeper exploration of StormStack
* :doc:`/tutorials/first-module` - Create your own game module
* :doc:`/lightning/cli/commands` - Full CLI command reference
* :doc:`/concepts/ecs-basics` - Learn about the Entity Component System

Web Dashboard
-------------

You can also manage StormStack through the web dashboard:

.. code-block:: text

   http://localhost:8080/admin/dashboard

Log in with your admin credentials to:

* View containers and matches
* Inspect ECS state
* Send commands
* Monitor the cluster
