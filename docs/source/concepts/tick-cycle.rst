==========
Tick Cycle
==========

.. note::

   This page is under development.

StormStack uses a tick-based simulation model where game state advances in discrete steps.

The Tick Lifecycle
------------------

Each tick follows this sequence:

1. **Commands Execute** - Queued commands are processed
2. **Systems Run** - Module systems update entity state
3. **Snapshot Generated** - Current state is captured
4. **Broadcast** - Snapshots are sent to connected clients via WebSocket

.. code-block:: text

   Commands Queue -> Tick Advances -> Systems Run -> Snapshot Generated -> WebSocket Broadcast

Tick Control
------------

Ticks can be advanced:

- **Manually** - Using ``lightning node simulation tick``
- **Automatically** - Using ``lightning node simulation play --interval-ms 16`` (60 FPS)

See :doc:`/tutorials/hello-stormstack` for hands-on examples.
