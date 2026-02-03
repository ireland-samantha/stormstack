====================
Execution Containers
====================

.. note::

   This page is under development.

Execution Containers provide complete runtime isolation for game matches.

What is a Container?
--------------------

Each container has:

- Its own **ClassLoader** (module isolation)
- Its own **ECS store** (entity/component data)
- Its own **game loop** (independent tick rate)
- Its own **command queue**

This isolation allows multiple games to run on a single JVM without interference.

Container Lifecycle
-------------------

1. **Create** - ``lightning deploy --modules X,Y``
2. **Start** - Tick advancement begins
3. **Run** - Matches execute within the container
4. **Stop** - Tick advancement pauses
5. **Delete** - Container resources are freed

See :doc:`/tutorials/hello-stormstack` for hands-on examples.
