=============
ECS Internals
=============

.. note::

   This page is under development.

This document describes the internal implementation of StormStack's ECS system.

Array Pool Memory Layout
------------------------

The ``ArrayEntityComponentStore`` uses a flat float array pool for O(1) component access:

- **Row-major storage** - Each entity gets a contiguous memory block
- **Float.NaN sentinel** - Used to mark null/unset values (no extra memory overhead)
- **Memory reclamation** - ``reclaimedRows`` FIFO queue prevents fragmentation

Key Source Files
----------------

- ``EntityComponentStore.java`` - Core interface
- ``ArrayEntityComponentStore.java`` - Flat array implementation
- ``DirtyTrackingEntityComponentStore.java`` - Change tracking decorator
